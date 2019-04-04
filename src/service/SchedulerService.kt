package com.bilbo.service

import com.bilbo.model.User
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.joda.time.DateTime
import java.util.*
import kotlin.concurrent.fixedRateTimer


@KtorExperimentalAPI
suspend fun doDeposit(user: User) {
    val billService = BillService()
    val depositService = DepositService()
    val monzoApi = MonzoApiService()

    val dueDeposits = billService.getBillsDueForDeposit(DateTime(), user)

    if (dueDeposits.count() == 0) return

    val totalAmount = dueDeposits.sumBy { it.amount }
    val billNames = dueDeposits.map { it.name }

    val monzoDeposit = MonzoDeposit(
        user.mainAccountId!!,
        totalAmount.toString(),
        UUID.randomUUID().toString()
    )
    println("Depositing $totalAmount into ${user.email}'s pot")
    monzoApi.depositIntoBilboPot(user.monzoToken!!, user.bilboPotId!!, monzoDeposit)

    dueDeposits.map { depositService.makeDeposit(it.id, it.amount) }
}

@KtorExperimentalAPI
fun makeDeposits() {
    val userService = UserService()

    GlobalScope.launch {
        val users = userService.getReadyUsers()

        for (user in users) {
            GlobalScope.launch {
                doDeposit(user)
            }
        }
    }


}


class SchedulerService {
    private lateinit var timer: Timer

    @KtorExperimentalAPI
    fun init() {
        timer = fixedRateTimer("task_scheduler", period = 10000.toLong()) {
            makeDeposits()
        }
    }

    fun cancel() {
        if (::timer.isInitialized) {
            timer.cancel()
        }
    }
}