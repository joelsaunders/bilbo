package com.bilbo.service
import io.ktor.util.KtorExperimentalAPI
import java.util.*
import kotlin.concurrent.fixedRateTimer


@KtorExperimentalAPI
suspend fun makeDeposits() {

}


class SchedulerService {
    private lateinit var timer: Timer

    fun init() {
        timer = fixedRateTimer("task_scheduler", period = 5000.toLong()) {
            println("task")
        }
    }

    fun cancel() {
        if (::timer.isInitialized) {
            timer.cancel()
        }
    }
}