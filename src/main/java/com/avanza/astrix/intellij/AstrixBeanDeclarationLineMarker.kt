package com.avanza.astrix.intellij

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.codeInsight.daemon.MergeableLineMarkerInfo
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.concurrency.JobLauncher
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.util.NotNullLazyValue.lazy
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class AstrixBeanDeclarationLineMarker : LineMarkerProviderDescriptor() {

    private val astrixIcon = Icons.Gutter.asterisk
    private val beanOption = Option("astrix.bean", "Astrix bean", astrixIcon)

    override fun getLineMarkerInfo(element: PsiElement) = null

    override fun collectSlowLineMarkers(elements: List<PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
        ApplicationManager.getApplication().assertReadAccessAllowed()
        if (beanOption.isEnabled) {
            val lock = ReentrantLock()
            invokeConcurrentlyUnderProgress(elements) {
                createLineMarkerInfo(it)?.apply { lock.withLock { result.add(this) } }
            }
        }
    }

    private fun createLineMarkerInfo(element: PsiElement): MergeableLineMarkerInfo<*>? {
        val method = (element as? PsiIdentifier)?.parent as? PsiMethod ?: return null
        if (AstrixContextUtility.isBeanDeclaration(method)) {
            return NavigationGutterIconBuilder.create(astrixIcon)
                .setEmptyPopupText("No astrix bean usages found.")
                .setTargets(lazy {
                    ConcurrentLinkedQueue<PsiMethodCallExpression>().apply {
                        invokeConcurrentlyUnderProgress(AstrixContextUtility.findBeanUsages(method)) {
                            addAll(it.findAll())
                        }
                    }
                })
                .createLineMarkerInfo(element)
        }
        return null
    }

    override fun getName() =
        "Astrix bean declaration"

    override fun getOptions() =
        arrayOf(beanOption)

    private fun <T> invokeConcurrentlyUnderProgress(things: List<T>, thingsProcessor: (T) -> Unit) {
        JobLauncher.getInstance().invokeConcurrentlyUnderProgress(things, ProgressIndicatorProvider.getGlobalProgressIndicator()) {
            thingsProcessor(it)
            true
        }
    }
}