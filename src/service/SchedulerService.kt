package com.bilbo.service

import com.bilbo.model.User
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.joda.time.DateTime
import java.util.*
import kotlin.concurrent.fixedRateTimer


private val logger = KotlinLogging.logger {}


//@KtorExperimentalAPI
//suspend fun doDeposit(user: User) {
//    val depositService = DepositService()
//    val monzoApi = MonzoApiService()
//    val billService = BillService()
//
//    val amounts = billService.getDepositAmountsThisPeriod(user)
//    val totalAmount = amounts.sumBy { it.getValue("amount") }
//
//
//    val monzoDeposit = MonzoDeposit(
//        user.mainAccountId!!,
//        totalAmount.toString(),
//        UUID.randomUUID().toString()
//    )
//    logger.debug { "Depositing $totalAmount into ${user.email}'s pot" }
//    monzoApi.depositIntoBilboPot(user, monzoDeposit)
//    monzoApi.postFeedItem(
//        user,
//        "Bilbo's pot increased",
//        "\uD83D\uDC47 ${amounts.count()} bills added"
//    )
//    amounts.map { depositService.makeDeposit(it.getValue("id"), it.getValue("amount")) }
//}
//
//@KtorExperimentalAPI
//suspend fun doWithdraw(user: User) {
//    val billService = BillService()
//    val withdrawalService = WithdrawalService()
//    val monzoApi = MonzoApiService()
//
//    //todo: handle periodic bills here
//    val dueWithdrawals = billService.getBillsDueForWithdrawal(DateTime(), user)
//
//    if (dueWithdrawals.count() == 0) return
//
//    val totalAmount = dueWithdrawals.sumBy { it.amount }
//
//    logger.debug { "Withdrawing ${dueWithdrawals.count()} bills" }
//    monzoApi.withdrawFromBilboPot(user, totalAmount)
//    monzoApi.postFeedItem(
//        user,
//        "Bilbo's pot decreased",
//        "\uD83D\uDC47 ${dueWithdrawals.count()} bills due today"
//    )
//    dueWithdrawals.map { withdrawalService.makeWithdrawal(it.id, true) }
//}

@KtorExperimentalAPI
fun makeDeposits() {
    val userService = UserService()

//    GlobalScope.launch {
//        val users = userService.getReadyUsers()
//        logger.debug { "${users.count()} users found" }
//
//        for (user in users) {
//            GlobalScope.launch {
//                doDeposit(user)
//            }
//            GlobalScope.launch {
//                doWithdraw(user)
//            }
//        }
//    }
}


class SchedulerService {
    private lateinit var timer: Timer

    @KtorExperimentalAPI
    fun init() {
        timer = fixedRateTimer("task_scheduler", period = 1000*60.toLong()) {
            makeDeposits()
        }
    }

    fun cancel() {
        if (::timer.isInitialized) {
            timer.cancel()
        }
    }
}