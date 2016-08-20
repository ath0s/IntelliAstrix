package com.avanza.astrix.intellij;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

public class AstrixContextUtility {

    private static final String ASTRIX_FQN = "com.avanza.astrix.context.Astrix";
    private static final String LIBRARY_FQN = "com.avanza.astrix.provider.core.Library";
    private static final String SERVICE_FQN = "com.avanza.astrix.provider.core.Service";
    private static final String API_PROVIDER_FQN = "com.avanza.astrix.provider.core.AstrixApiProvider";

    public static boolean isAstrixBeanRetriever(PsiMethod method) {
        String qualifiedClassName = Optional.ofNullable(method.getContainingClass()).map(PsiClass::getQualifiedName).orElse(null);
        String methodName = method.getName();
        return ASTRIX_FQN.equals(qualifiedClassName) &&
                ("getBean".equals(methodName) || "waitForBean".equals(methodName));
    }

    public static boolean isBeanDeclaration(PsiMethod method) {
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return false;
        }
        PsiAnnotationOwner classAnnotations = containingClass.getModifierList();
        if (classAnnotations == null) {
            return false;
        }
        PsiAnnotationOwner annotations = method.getModifierList();
        return classAnnotations.findAnnotation(API_PROVIDER_FQN) != null && (annotations.findAnnotation(SERVICE_FQN) != null || annotations.findAnnotation(LIBRARY_FQN) != null);
    }

    public static Optional<PsiMethod> findBeanDeclaration(PsiExpressionList parameters) {
        Module module = ModuleUtil.findModuleForPsiElement(parameters);
        if (module == null) {
            return Optional.empty();
        }

        GlobalSearchScope searchScope = module.getModuleRuntimeScope(false);
        JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(parameters.getProject());

        PsiType typeParameter = getTypeParameter(parameters);
        if (typeParameter == null) {
            return Optional.empty();
        }

        PsiClass apiProviderAnnotation = javaPsiFacade.findClass(API_PROVIDER_FQN, searchScope);
        if (apiProviderAnnotation == null) {
            return Optional.empty();
        }
        return AnnotatedElementsSearch.searchPsiClasses(apiProviderAnnotation, searchScope).findAll()
                .stream()
                .map(PsiClass::getMethods) // TODO: getAllMethods?
                .flatMap(Arrays::stream)
                .filter(method -> method.getModifierList().findAnnotation(SERVICE_FQN) != null || method.getModifierList().findAnnotation(LIBRARY_FQN) != null)
                .filter(method -> {
                    PsiType returnType = method.getReturnType();
                    // TODO: AstrixQualifier
                    return returnType != null && typeParameter.isAssignableFrom(returnType);
                }).findFirst();
    }

    public static Collection<PsiMethodCallExpression> findBeanUsages(PsiMethod method) {
        Module module = ModuleUtil.findModuleForPsiElement(method);
        if (module == null) {
            return emptyList();
        }

        GlobalSearchScope searchScope = module.getModuleRuntimeScope(false);
        JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(method.getProject());

        PsiClass astrixInterface = javaPsiFacade.findClass(ASTRIX_FQN, searchScope);
        if (astrixInterface == null) {
            return emptyList();
        }
        PsiMethod[] getBeanMethods = astrixInterface.findMethodsByName("getBean", true);
        PsiMethod[] waitForBeanMethods = astrixInterface.findMethodsByName("waitForBean", true);
        PsiType returnType = method.getReturnType();
        if (returnType == null) {
            return emptyList();
        }
        return Stream.concat(Arrays.stream(getBeanMethods), Arrays.stream(waitForBeanMethods))
                .map(MethodReferencesSearch::search)
                .flatMap(query -> query.findAll().stream())
                .filter(PsiReferenceExpression.class::isInstance)
                .map(PsiReferenceExpression.class::cast)
                .map(PsiReferenceExpression::getContext)
                .filter(PsiMethodCallExpression.class::isInstance)
                .map(PsiMethodCallExpression.class::cast)
                .filter(psiMethodCallExpression -> {
                    // TODO: AstrixQualifier
                    PsiType typeParameter = getTypeParameter(psiMethodCallExpression.getArgumentList());
                    return typeParameter != null && typeParameter.isAssignableFrom(returnType);
                })
                .collect(toList());
    }

    @Nullable
    private static PsiType getTypeParameter(PsiExpressionList parameters) {
        PsiExpression[] expressions = parameters.getExpressions();
        if (expressions.length < 1) {
            return null;
        }
        // assuming class parameter is the first
        PsiExpression psiExpression = expressions[0];

        if (!(psiExpression instanceof PsiClassObjectAccessExpression)) {
            return null;
        }
        PsiClassObjectAccessExpression classObjectAccessExpression = (PsiClassObjectAccessExpression) psiExpression;
        return classObjectAccessExpression.getOperand().getType();
    }

}
