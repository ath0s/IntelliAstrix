package com.avanza.astrix.intellij;

import com.avanza.astrix.intellij.query.QueryChain;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotationOwner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassObjectAccessExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchScopeUtil;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import static com.intellij.openapi.util.text.StringUtil.trimEnd;
import static com.intellij.psi.PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME;
import static com.intellij.psi.util.PsiUtil.skipParenthesizedExprDown;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

public class AstrixContextUtility {

    private static final String ASTRIX_FQN = "com.avanza.astrix.context.Astrix";
    private static final String LIBRARY_FQN = "com.avanza.astrix.provider.core.Library";
    private static final String SERVICE_FQN = "com.avanza.astrix.provider.core.Service";
    private static final String QUALIFIER_FQN = "com.avanza.astrix.provider.core.AstrixQualifier";
    private static final String API_PROVIDER_FQN = "com.avanza.astrix.provider.core.AstrixApiProvider";
    private static final String REACTIVE_POSTFIX = "Async";
    private static final Set<String> BEAN_RETRIEVAL_METHOD_NAMES = Set.of("getBean", "waitForBean");

    public static boolean isAstrixBeanRetriever(@Nullable PsiMethod method) {
        if(method == null) {
            return false;
        }

        String qualifiedClassName = Optional.ofNullable(method.getContainingClass()).map(PsiClass::getQualifiedName).orElse(null);
        String methodName = method.getName();
        return ASTRIX_FQN.equals(qualifiedClassName) && BEAN_RETRIEVAL_METHOD_NAMES.contains(methodName);
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

        Predicate<PsiType> isRequestedType = isSameOrReactiveType(typeParameter, psiExpressionList.getProject());

        String qualifierParameter = getQualifier(psiExpressionList);
        return psiMethod -> {
            PsiType beanType = psiMethod.getReturnType();
            String beanQualifier = getQualifier(psiMethod);
            return beanType != null && isRequestedType.test(beanType) && Objects.equals(qualifierParameter, beanQualifier);
        };
    }

    public static Collection<Query<PsiMethodCallExpression>> findBeanUsages(PsiMethod method) {
        PsiType beanType = method.getReturnType();
        if (beanType == null) {
            return emptyList();
        }

        String beanQualifier = getQualifier(method);

        Project project = method.getProject();
        Optional<GlobalSearchScope> maybeSearchScope = Arrays.stream(ModuleManager.getInstance(project).getModules())
                                                             .map(module -> module.getModuleRuntimeScope(true))
                                                             .filter(globalSearchScope -> PsiSearchScopeUtil.isInScope(globalSearchScope, method))
                                                             .reduce(GlobalSearchScope::union);

        if (maybeSearchScope.isEmpty()) {
            return emptyList();
        }

        GlobalSearchScope searchScope = maybeSearchScope.get();

        JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(project);

        PsiClass astrixInterface = javaPsiFacade.findClass(ASTRIX_FQN, GlobalSearchScope.allScope(project));
        if (astrixInterface == null) {
            return emptyList();
        }

        return BEAN_RETRIEVAL_METHOD_NAMES.stream().flatMap(methodName -> Arrays.stream(astrixInterface.findMethodsByName(methodName, true)))
                     .map(psiMethod -> MethodReferencesSearch.search(psiMethod, searchScope, true))
                     .map(query -> new QueryChain<>(query).instanceOf(PsiReferenceExpression.class)
                                                          .map(PsiReferenceExpression::getContext)
                                                          .instanceOf(PsiMethodCallExpression.class)
                                                          .filter(psiMethodCallExpression -> {
                                                              PsiExpressionList parameters = psiMethodCallExpression.getArgumentList();
                                                              PsiType typeParameter = getTypeParameter(parameters);
                                                              String qualifierParameter = getQualifier(parameters);
                                                              return typeParameter != null &&
                                                                      isSameOrReactiveType(typeParameter, parameters.getProject()).test(beanType) &&
                                                                      Objects.equals(qualifierParameter, beanQualifier);
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
        PsiExpression psiExpression = skipParenthesizedExprDown(expressions[0]);

        if (!(psiExpression instanceof PsiClassObjectAccessExpression)) {
            return null;
        }
        PsiClassObjectAccessExpression classObjectAccessExpression = (PsiClassObjectAccessExpression) psiExpression;
        return classObjectAccessExpression.getOperand().getType();
    }

    @Nullable
    private static String getQualifier(PsiExpressionList parameters) {
        PsiExpression[] expressions = parameters.getExpressions();
        if (expressions.length < 2) {
            return null;
        }
        // assuming qualifier parameter is the second
        PsiExpression psiExpression = skipParenthesizedExprDown(expressions[1]);

        return resolveValue(psiExpression);
    }

    @Nullable
    private static String getQualifier(PsiMethod method) {
        return Optional.ofNullable(method.getModifierList().findAnnotation(QUALIFIER_FQN))
                       .flatMap(annotation -> Optional.ofNullable(annotation.findDeclaredAttributeValue(DEFAULT_REFERENCED_METHOD_NAME)))
                       .filter(PsiExpression.class::isInstance)
                       .map(PsiExpression.class::cast)
                       .map(AstrixContextUtility::resolveValue)
                       .orElse(null);
    }

    @Nullable
    private static String resolveValue(@Nullable PsiExpression psiExpression) {
        if(psiExpression instanceof PsiReferenceExpression) {
            PsiReferenceExpression psiReferenceExpression = (PsiReferenceExpression) psiExpression;
            PsiElement psiElement = psiReferenceExpression.resolve();
            if(psiElement instanceof PsiVariable) {
                PsiVariable psiVariable = (PsiVariable) psiElement;
                psiExpression = psiVariable.getInitializer();
            }
        }

        Object constantExpression = JavaConstantExpressionEvaluator.computeConstantExpression(psiExpression, false);
        return constantExpression instanceof String? (String) constantExpression : null;
    }

    private static Predicate<PsiType> isSameOrReactiveType(@NotNull PsiType requestedType, Project project) {
        Predicate<PsiType> matchesType = requestedType::isAssignableFrom;

        String canonicalText = requestedType.getCanonicalText();
        if(canonicalText.endsWith(REACTIVE_POSTFIX)) {
            String nonReactiveTypeName = trimEnd(canonicalText, REACTIVE_POSTFIX);
            PsiType nonReactiveType = PsiType.getTypeByName(nonReactiveTypeName, project, requestedType.getResolveScope());
            return matchesType.or(nonReactiveType::isAssignableFrom);
        } else {
            return matchesType;
        }
    }
}
