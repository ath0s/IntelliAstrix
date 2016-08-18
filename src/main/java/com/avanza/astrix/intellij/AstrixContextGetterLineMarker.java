package com.avanza.astrix.intellij;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class AstrixContextGetterLineMarker extends RelatedItemLineMarkerProvider {
    private final Option getterOption = new Option("astrix.getter", "Astrix getter", Icons.Gutter.asterisk);

    @Override
    protected void collectNavigationMarkers(@NotNull PsiElement element, Collection<? super RelatedItemLineMarkerInfo> result) {
        PsiElement parent;
        if (element instanceof PsiReferenceExpression && getterOption.isEnabled() && (parent = element.getParent()) instanceof PsiMethodCallExpression) {
            PsiMethodCallExpression psiMethodCallExpression = (PsiMethodCallExpression) parent;
            AstrixContextUtility utility = new AstrixContextUtility();

            if (utility.isAstrixBeanRetriever(psiMethodCallExpression.resolveMethod())) {
                utility.findBeanDeclaration(psiMethodCallExpression.getArgumentList())
                        .map(method -> NavigationGutterIconBuilder.create(getterOption.getIcon())
                                .setTarget(method.getNameIdentifier())
                                .setTooltipText("Navigate to declaration of " + method.getReturnType().getPresentableText())
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
