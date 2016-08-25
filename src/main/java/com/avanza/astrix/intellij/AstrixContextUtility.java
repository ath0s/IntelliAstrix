package com.avanza.astrix.intellij;

import com.avanza.astrix.intellij.query.QueryChain;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
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
        if (classAnnotations == null) {
            return false;
        }
        PsiAnnotationOwner annotations = method.getModifierList();
        return classAnnotations.findAnnotation(API_PROVIDER_FQN) != null && (annotations.findAnnotation(SERVICE_FQN) != null || annotations.findAnnotation(LIBRARY_FQN) != null);
    }

    public static Collection<PsiMethod> getBeanDeclarationCandidates(Module module) {
        GlobalSearchScope searchScope = module.getModuleRuntimeScope(false);
        JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(module.getProject());
        PsiClass apiProviderAnnotation = javaPsiFacade.findClass(API_PROVIDER_FQN, searchScope);
        if (apiProviderAnnotation == null) {
            return emptyList();
        }
        return AnnotatedElementsSearch.searchPsiClasses(apiProviderAnnotation, searchScope).findAll()
                .stream()
                .map(PsiClass::getMethods) // TODO: getAllMethods?
                .flatMap(Arrays::stream)
                .filter(method -> method.getModifierList().findAnnotation(SERVICE_FQN) != null || method.getModifierList().findAnnotation(LIBRARY_FQN) != null)
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
        VirtualFile virtualFile = method.getContainingFile().getOriginalFile().getVirtualFile();
        Optional<GlobalSearchScope> maybeSearchScope = Arrays.stream(ModuleManager.getInstance(project).getModules())
                                                             .map(module -> module.getModuleRuntimeScope(false))
                                                             .filter(globalSearchScope -> globalSearchScope.contains(virtualFile))
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
