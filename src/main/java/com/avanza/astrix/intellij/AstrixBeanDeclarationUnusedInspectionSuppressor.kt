package com.avanza.astrix.intellij

import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase.ALTERNATIVE_ID
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase.SHORT_NAME
import com.intellij.concurrency.JobLauncher
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod

class AstrixBeanDeclarationUnusedInspectionSuppressor : InspectionSuppressor {

    private val toolIds = setOf(SHORT_NAME, ALTERNATIVE_ID)

    override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean {
        if (toolId !in toolIds) {
            return false
        }
        val method = element.parent as? PsiMethod ?: return false
        if (!AstrixContextUtility.isBeanDeclaration(method)) {
            return false
        }
        return !JobLauncher.getInstance().invokeConcurrentlyUnderProgress(AstrixContextUtility.findBeanUsages(method), ProgressIndicatorProvider.getGlobalProgressIndicator()) {
            it.findFirst() != null
        }
    }

    override fun getSuppressActions(element: PsiElement?, toolId: String) =
        emptyArray<SuppressQuickFix>()
}