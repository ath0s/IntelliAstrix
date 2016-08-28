package com.avanza.astrix.intellij;

import com.avanza.astrix.intellij.query.QueryChain;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchScopeUtil;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.util.Query;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Predicate;
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
        return classAnnotations != null && classAnnotations.findAnnotation(API_PROVIDER_FQN) != null && (isService(method) || isLibrary(method));
    }

    public static boolean isService(PsiMethod method) {
        return method.getModifierList().findAnnotation(SERVICE_FQN) != null;
    }

    public static boolean isLibrary(PsiMethod method) {
        return method.getModifierList().findAnnotation(LIBRARY_FQN) != null;
    }

    public static Collection<PsiMethod> getBeanDeclarationCandidates(GlobalSearchScope globalSearchScope, Project project) {
        JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(project);
        PsiClass apiProviderAnnotation = javaPsiFacade.findClass(API_PROVIDER_FQN, globalSearchScope);
        if (apiProviderAnnotation == null) {
            return emptyList();
        }
        Predicate<PsiMethod> isService = AstrixContextUtility::isService;
        Predicate<PsiMethod> isLibrary = AstrixContextUtility::isLibrary;
        return AnnotatedElementsSearch.searchPsiClasses(apiProviderAnnotation, globalSearchScope).findAll()
                                      .stream()
                                      .flatMap(psiClass -> Arrays.stream(psiClass.getMethods())) // TODO: getAllMethods?
                                      .filter(isService.or(isLibrary))
                                      .collect(toList());
    }

    public static Predicate<PsiMethod> isBeanDeclaration(PsiExpressionList psiExpressionList) {
        PsiType typeParameter = getTypeParameter(psiExpressionList);
        if (typeParameter == null) {
            return psiMethod -> false;
        }

        return psiMethod -> {
            PsiType returnType = psiMethod.getReturnType();
            // TODO: AstrixQualifier
            return returnType != null && typeParameter.isAssignableFrom(returnType);
        };
    }

    public static Collection<Query<PsiMethodCallExpression>> findBeanUsages(PsiMethod method) {
        PsiType returnType = method.getReturnType();
        if (returnType == null) {
            return emptyList();
        }

        Project project = method.getProject();
        Optional<GlobalSearchScope> maybeSearchScope = Arrays.stream(ModuleManager.getInstance(project).getModules())
                                                             .map(module -> module.getModuleRuntimeScope(true))
                                                             .filter(globalSearchScope -> PsiSearchScopeUtil.isInScope(globalSearchScope, method))
                                                             .reduce(GlobalSearchScope::union);

        if (!maybeSearchScope.isPresent()) {
            return emptyList();
        }

        GlobalSearchScope searchScope = maybeSearchScope.get();

        JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(project);

        PsiClass astrixInterface = javaPsiFacade.findClass(ASTRIX_FQN, GlobalSearchScope.allScope(project));
        if (astrixInterface == null) {
            return emptyList();
        }
        PsiMethod[] getBeanMethods = astrixInterface.findMethodsByName("getBean", true);
        PsiMethod[] waitForBeanMethods = astrixInterface.findMethodsByName("waitForBean", true);

        return Stream.concat(Arrays.stream(getBeanMethods), Arrays.stream(waitForBeanMethods))
                     .map(psiMethod -> MethodReferencesSearch.search(psiMethod, searchScope, true))
                     .map(query -> new QueryChain<>(query).instanceOf(PsiReferenceExpression.class)
                                                          .map(PsiReferenceExpression::getContext)
                                                          .instanceOf(PsiMethodCallExpression.class)
                                                          .filter(psiMethodCallExpression -> {
                                                              // TODO: AstrixQualifier
                                                              PsiType typeParameter = getTypeParameter(psiMethodCallExpression.getArgumentList());
                                                              return typeParameter != null && typeParameter.isAssignableFrom(returnType);
                                                          })
                                                          .query())
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
