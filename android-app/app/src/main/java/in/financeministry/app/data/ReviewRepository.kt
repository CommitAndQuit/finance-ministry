package `in`.financeministry.app.data

import android.app.NotificationManager
import java.util.UUID

/** Retain fingerprint and financial evidence; dismissing is not deletion or confirmation. */
suspend fun TransactionRepository.dismissReview(id:String,expectedUpdatedAt:Long) = changeReviewState(id,expectedUpdatedAt,"NeedsReview","Dismissed")

/** Restoration returns to uncertainty, never directly to counted money. */
suspend fun TransactionRepository.restoreDismissedReview(id:String,expectedUpdatedAt:Long) = changeReviewState(id,expectedUpdatedAt,"Dismissed","NeedsReview")

private suspend fun TransactionRepository.changeReviewState(id:String,expectedUpdatedAt:Long,before:String,after:String) = withLedger {db->
    val dao=db.transactions()
    val old=requireNotNull(dao.get(id)) {"This record no longer exists."}
    require(old.updatedAt==expectedUpdatedAt && old.reviewState==before) {"This record changed. Reopen it before trying again."}
    require(!dao.hasActiveFinancialMatch(id) && dao.repaymentsFor(id).isEmpty() && dao.allocationsFrom(id).isEmpty()) {
        "Undo matches and repayment links before changing this record."
    }
    val now=maxOf(System.currentTimeMillis(),Math.addExact(old.updatedAt,1))
    db.runInTransaction {
        dao.update(old.copy(reviewState=after,isUserCorrected=true,updatedAt=now))
        dao.audit(listOf(CorrectionEntity(UUID.randomUUID().toString(),id,now,"reviewState",before,after)))
    }
    if(after=="Dismissed") applicationContext.getSystemService(NotificationManager::class.java).cancel(id,1)
    revision.value++
}
