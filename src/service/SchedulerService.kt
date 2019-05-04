package com.bilbo.service

import com.bilbo.model.User
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import mu.KotlinLogging
import java.util.*
import kotlin.concurrent.fixedRateTimer


private val logger = KotlinLogging.logger {}


@KtorExperimentalAPI
suspend fun doDeposit(user: User) {
    val depositService = DepositService()
    val monzoApi = MonzoApiService()
    val billService = BillService()

    val dueBills = billService.getDueDeposits(user)
    val totalAmount = dueBills.map { it.key.amount * it.value.count() }.sum()

    if (dueBills.count() == 0) return

    val monzoDeposit = MonzoDeposit(
        user.mainAccountId!!,
        totalAmount.toString(),
        UUID.randomUUID().toString()
    )
    logger.debug { "Depositing $totalAmount into ${user.email}'s pot" }
    monzoApi.depositIntoBilboPot(user, monzoDeposit)
    monzoApi.postFeedItem(
        user,
        "Bilbo's pot increased",
        "\uD83D\uDC47 ${dueBills.count()} bills added"
    )
    dueBills.map { depositService.makeDeposit(it.key.id, it.key.amount * it.value.count()) }
}

@KtorExperimentalAPI
suspend fun doWithdraw(user: User) {
    val billService = BillService()
    val withdrawalService = WithdrawalService()
    val monzoApi = MonzoApiService()

    val dueWithdrawals = billService.getDueWithdrawals(user)
    val totalAmount = dueWithdrawals.map { it.amount }.sum()

    if (dueWithdrawals.count() == 0) return

    logger.debug { "Withdrawing ${dueWithdrawals.count()} bills" }

    try {
        monzoApi.withdrawFromBilboPot(user, totalAmount)
        monzoApi.postFeedItem(
            user,
            "Bilbo's pot decreased",
            "\uD83D\uDC47 ${dueWithdrawals.count()} bills due today"
        )
        dueWithdrawals.map { withdrawalService.makeWithdrawal(it.id, true) }
    } catch (e: Exception) {
        dueWithdrawals.map { withdrawalService.makeWithdrawal(it.id, false) }
    }
}

@KtorExperimentalAPI
fun launchActions() {
    val userService = UserService()

    GlobalScope.launch {
        val users = userService.getReadyUsers()
        logger.info { "${users.count()} fully set up users found" }

        for (user in users) {
            GlobalScope.launch {
                doDeposit(user)
            }
            GlobalScope.launch {
                doWithdraw(user)
            }
        }
    }
}

class SchedulerService {
    private lateinit var timer: Timer

    @KtorExperimentalAPI
    fun init() {
        if (this::timer.isInitialized) {
            timer.cancel()
        }
        timer = fixedRateTimer("task_scheduler", period = 1000*60*2.toLong()) {
            launchActions()
        }
    }

    fun cancel() {
        if (::timer.isInitialized) {
            timer.cancel()
        }
    }
}