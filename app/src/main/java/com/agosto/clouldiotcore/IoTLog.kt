package com.agosto.clouldiotcore

import android.util.Log
import com.google.gson.Gson
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import java.lang.Exception
import java.util.concurrent.TimeUnit

data class DeviceLogs(val data: List<String>, val tags: List<HashMap<String,String>>)

object IoTLog {
    const val TAG = "IoTLog"

    const val NO_LOGS = 0
    const val WARN = 1
    const val INFO = 2
    const val DEBUG = 3
    const val VERBOSE = 4
    var BATCH_SIZE = 10
    var PURGE_THRESHOLD = 1000
    var disposable : Disposable? = null
    var pollingTimeLong = 60L
    var pollingTimeShort = 3L
    var pollingUnit = TimeUnit.SECONDS

    val remoteLogs: MutableList<String> = mutableListOf("${BuildConfig.APPLICATION_ID} ${BuildConfig.VERSION_NAME} startup")
    val labels : MutableMap<String,String> = mutableMapOf(Pair("os","android"))

    var level = NO_LOGS

    var iotCoreClient: IotCoreClient? = null

    fun d(tag:String, msg: String) {
        Log.d(tag,msg)
        if(level >= DEBUG) {
            addLog(tag, msg)
        }
    }

    fun i(tag:String, msg: String) {
        Log.i(tag,msg)
        if(level >= INFO) {
            addLog(tag, msg)
        }
    }

    fun w(tag:String, msg: String) {
        Log.w(tag,msg)
        if(level >= WARN) {
            addLog(tag, msg)
        }
    }

    fun w(tag: String, t: Throwable) {
        Log.w(tag,t)
        if(level >= WARN) {
            addLog(tag, "$t")
        }
    }

    fun v(tag:String, msg: String) {
        Log.v(tag,msg)
        if(level >= VERBOSE) {
            addLog(tag, msg)
        }
    }

    private fun addLog(tag: String, msg: String) {
        remoteLogs.add("$tag: $msg")
        /*if(remoteLogs.size > POST_THRESHOLD) {
            postLogs()
        }*/
    }


    fun popLogs() : Observable<DeviceLogs> {
        return Observable.create { emitter ->
            val tags : MutableList<HashMap<String,String>> = mutableListOf()
            labels.forEach {
                tags.add(hashMapOf(Pair(it.key,it.value)))
            }
            var bytes = 0;
            val logs = remoteLogs.takeWhile {
                bytes += it.toByteArray().size
                bytes < IotCoreClient.MAX_PUB_SIZE_BYTES
            }
            val playerLogs = DeviceLogs(logs, tags)
            Log.i(TAG,"Posting ${playerLogs.data.size} ($bytes bytes) logs of ${remoteLogs.size}")
            Log.v(TAG,Gson().toJson(playerLogs))
            emitter.onNext(playerLogs)
            emitter.onComplete()
        }
    }

    fun postLogs(deviceLogs: DeviceLogs) : Observable<DeviceLogs> {
        return Observable.create {
            if(deviceLogs.data.isEmpty()) {
                Log.i(TAG,"No Logs to post")
                it.onNext(deviceLogs)
                it.onComplete()
            } else if(iotCoreClient?.publishTelemetry(Gson().toJson(deviceLogs)) == true) {
                it.onNext(deviceLogs)
                it.onComplete()
            } else {
                it.onError(Exception("Failed to post logs via MQTT"))
            }
        }
        //return functionsApi.writePlayerLogs(playerLogs).map { playerLogs }
    }

    fun tryPostingLogs(): Observable<String> {
        return popLogs()
                .flatMap { postLogs(it) }
                .map {
                    remoteLogs.removeAll(it.data)
                    Log.i(TAG,"Removed ${it.data.size} logs, ${remoteLogs.size} logs remain")
                    "Logs posted with labels ${it.tags}"
                }
                .onErrorResumeNext { throwable: Throwable ->
                    if(remoteLogs.size > PURGE_THRESHOLD) {
                        remoteLogs.clear()
                        w(TAG,"Purge level reached")
                    }
                    Observable.just("error: $throwable")
                }
                .repeatWhen { err ->
                    err.flatMap {
                        if(remoteLogs.size > BATCH_SIZE) {
                            Log.i(TAG,"${remoteLogs.size} logs remain, repeating in $pollingTimeShort secs")
                            Observable.timer(pollingTimeShort, pollingUnit)
                        } else {
                            Log.i(TAG,"${remoteLogs.size} logs remain, repeating in $pollingTimeLong secs")
                            Observable.timer(pollingTimeLong, pollingUnit)
                        }
                    }
                }
    }

    fun stopPosting() {
        disposable?.dispose()
    }

    fun startPosting() {
        disposable?.dispose()
        Log.d(TAG,"Starting log posting every to $pollingTimeLong secs")
        disposable = tryPostingLogs()
                .subscribe(
                        {
                            Log.d(TAG,"on next: $it")
                        },
                        {
                            w(TAG,"logging fatal error: $it")
                        })
    }

    fun logsCount() : Int {
        return remoteLogs.size
    }

}
