package com.avanza.astrix.intellij

import com.avanza.astrix.intellij.query.filter
import com.avanza.astrix.intellij.query.instanceOf
import com.avanza.astrix.intellij.query.map
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiAnnotationOwner
import com.intellij.psi.PsiClassObjectAccessExpression
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiExpressionList
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiType
import com.intellij.psi.PsiVariable
import com.intellij.psi.impl.JavaConstantExpressionEvaluator
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchScopeUtil
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.intellij.psi.search.searches.MethodReferencesSearch
import com.intellij.psi.util.PsiUtil
import com.intellij.util.Query

internal object AstrixContextUtility {
    private const val ASTRIX_FQN = "com.avanza.astrix.context.Astrix"
    private const val LIBRARY_FQN = "com.avanza.astrix.provider.core.Library"
    private const val SERVICE_FQN = "com.avanza.astrix.provider.core.Service"
    private const val QUALIFIER_FQN = "com.avanza.astrix.provider.core.AstrixQualifier"
    private const val API_PROVIDER_FQN = "com.avanza.astrix.provider.core.AstrixApiProvider"
    private const val REACTIVE_POSTFIX = "Async"
    private val BEAN_RETRIEVAL_METHOD_NAMES = setOf("getBean", "waitForBean")

    val PsiMethod.isAstrixBeanRetriever: Boolean
        get() {
            val qualifiedClassName = containingClass?.qualifiedName
            val methodName = name
            return qualifiedClassName == ASTRIX_FQN && methodName in BEAN_RETRIEVAL_METHOD_NAMES
        }

    val PsiMethod.isBeanDeclaration: Boolean
        get() {
            val containingClass = containingClass ?: return false
            val classAnnotations: PsiAnnotationOwner? = containingClass.modifierList
            return classAnnotations?.findAnnotation(API_PROVIDER_FQN) != null && (isService || isLibrary)
        }

    val PsiMethod.isService
        get() = modifierList.findAnnotation(SERVICE_FQN) != null

    val PsiMethod.isLibrary
        get() = modifierList.findAnnotation(LIBRARY_FQN) != null

    fun getBeanDeclarationCandidates(globalSearchScope: GlobalSearchScope, project: Project): Collection<PsiMethod> {
        val javaPsiFacade = JavaPsiFacade.getInstance(project)
        val apiProviderAnnotation = javaPsiFacade.findClass(API_PROVIDER_FQN, globalSearchScope) ?: return emptyList()
        return AnnotatedElementsSearch.searchPsiClasses(apiProviderAnnotation, globalSearchScope)
            .asSequence()
            .flatMap { it.methods.asSequence() } // TODO: getAllMethods?
            .filter { it.isService || it.isLibrary }
            .toList()
    }

    fun isBeanDeclaration(psiExpressionList: PsiExpressionList): (PsiMethod) -> Boolean {
        val typeParameter = getTypeParameter(psiExpressionList) ?: return { false }
        val isRequestedType = isSameOrReactiveType(typeParameter, psiExpressionList.project)
        val qualifierParameter = getQualifier(psiExpressionList)
        return {
            val beanType = it.returnType
            val beanQualifier = getQualifier(it)
            beanType != null && isRequestedType(beanType) && qualifierParameter == beanQualifier
        }
    }

    fun PsiMethod.findBeanUsages(): List<Query<PsiMethodCallExpression>> {
        val beanType = returnType ?: return emptyList()
        val beanQualifier = getQualifier(this)
        val project = project
        val searchScope = ModuleManager.getInstance(project).modules
            .asSequence()
            .map { it.getModuleRuntimeScope(true) }
            .filter { PsiSearchScopeUtil.isInScope(it, this) }
            .reduceOrNull(GlobalSearchScope::union) ?: return emptyList()

        val javaPsiFacade = JavaPsiFacade.getInstance(project)
        val astrixInterface = javaPsiFacade.findClass(ASTRIX_FQN, GlobalSearchScope.allScope(project)) ?: return emptyList()
        return BEAN_RETRIEVAL_METHOD_NAMES.asSequence()
            .flatMap { astrixInterface.findMethodsByName(it, true).asSequence() }
            .map { MethodReferencesSearch.search(it, searchScope, true) }
            .map { query ->
                query.instanceOf<PsiReferenceExpression>()
                    .map { it.context }
                    .instanceOf<PsiMethodCallExpression>()
                    .filter {
                        val parameters = it.argumentList
                        val typeParameter = getTypeParameter(parameters)
                        val qualifierParameter = getQualifier(parameters)
                        typeParameter != null &&
                                isSameOrReactiveType(typeParameter, parameters.project)(beanType) &&
                                qualifierParameter == beanQualifier
                    }
            }
            .toList()
    }

    private fun getTypeParameter(parameters: PsiExpressionList): PsiType? {
        val expressions = parameters.expressions
        if (expressions.isEmpty()) {
            return null
        }
        // assuming class parameter is the first
        val psiExpression = PsiUtil.skipParenthesizedExprDown(expressions[0]) as? PsiClassObjectAccessExpression ?: return null
        return psiExpression.operand.type
    }

    private fun getQualifier(parameters: PsiExpressionList): String? {
        val expressions = parameters.expressions
        if (expressions.size < 2) {
            return null
        }
        // assuming qualifier parameter is the second
        val psiExpression = PsiUtil.skipParenthesizedExprDown(expressions[1])
        return resolveValue(psiExpression)
    }

    private fun getQualifier(method: PsiMethod) =
        method.modifierList.findAnnotation(QUALIFIER_FQN)
            ?.findDeclaredAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME)
            ?.let { it as? PsiExpression }
            .let { resolveValue(it) }

    private fun resolveValue(psiExpression: PsiExpression?): String? {
        var expression = psiExpression
        if (expression is PsiReferenceExpression) {
            val psiElement = expression.resolve()
            if (psiElement is PsiVariable) {
                expression = psiElement.initializer
            }
        }
        return JavaConstantExpressionEvaluator.computeConstantExpression(expression, false) as? String
    }

    private fun isSameOrReactiveType(requestedType: PsiType, project: Project): (PsiType) -> Boolean {
        val matchesType = requestedType::isAssignableFrom
        val canonicalText = requestedType.canonicalText
        return if (canonicalText.endsWith(REACTIVE_POSTFIX)) {
            val nonReactiveTypeName = canonicalText.removeSuffix(REACTIVE_POSTFIX)
            val nonReactiveType = PsiType.getTypeByName(nonReactiveTypeName, project, requestedType.resolveScope!!)
            matchesType or { nonReactiveType.isAssignableFrom(it) }
        } else {
            matchesType
        }
    }

    private infix fun <T> ((T) -> Boolean).or(other: (T) -> Boolean): (T) -> Boolean =
        { this(it) || other(it) }
}