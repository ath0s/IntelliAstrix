package com.avanza.astrix.intellij;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Optional;

import static com.avanza.astrix.intellij.AstrixContextUtility.findBeanDeclaration;
import static com.avanza.astrix.intellij.AstrixContextUtility.isAstrixBeanRetriever;

public class AstrixContextGetterLineMarker extends RelatedItemLineMarkerProvider {
    private final Option getterOption = new Option("astrix.getter", "Astrix getter", Icons.Gutter.asterisk);

    @Override
    protected void collectNavigationMarkers(@NotNull PsiElement element, Collection<? super RelatedItemLineMarkerInfo> result) {
        PsiElement parent;
        if (element instanceof PsiReferenceExpression && getterOption.isEnabled() && (parent = element.getParent()) instanceof PsiMethodCallExpression) {
            PsiMethodCallExpression psiMethodCallExpression = (PsiMethodCallExpression) parent;

            if (isAstrixBeanRetriever(psiMethodCallExpression.resolveMethod())) {
                findBeanDeclaration(psiMethodCallExpression.getArgumentList())
                        .map(method -> NavigationGutterIconBuilder.create(Icons.Gutter.asterisk)
                                .setTarget(method.getNameIdentifier())
                                .setTooltipText("Navigate to declaration of " + Optional.ofNullable(method.getReturnType()).map(PsiType::getPresentableText).orElse(""))
                                .createLineMarkerInfo(element))
                        .ifPresent(result::add);
            }
        }

    }

    @Nullable
    @Override
    public String getName() {
        return "Astrix line markers";
    }

    @Override
    public Option[] getOptions() {
        return new Option[]{getterOption};
    }
}
