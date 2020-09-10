package com.yilnz.intellij.runthismethod;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.process.KillableColoredProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.notification.Notification;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiMethodImpl;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.popup.list.ListPopupImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class RunThisMethod extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Sdk projectSdk = ProjectRootManager.getInstance(project).getProjectSdk();
        String homePath = projectSdk.getHomePath();
        //final RunManager runManager = RunManager.getInstance(project);
        ProcessHandler[] runningProcesses = ExecutionManager.getInstance(project).getRunningProcesses();
        ArrayList<String> strings = new ArrayList<>();
        for (ProcessHandler runningProcess : runningProcesses) {
            KillableColoredProcessHandler p = (KillableColoredProcessHandler) runningProcess;
            String commandLine = p.getCommandLine();
            String[] s = commandLine.split(" ");
            System.out.println("[RunThisMethod]class:" + s[s.length-1]);
            Matcher matcher = Pattern.compile("pid=(\\d+)").matcher(p.getProcess().toString());
            matcher.find();
            String pid = matcher.group(1);
            System.out.println("[RunThisMethod]pid:" + pid);
            strings.add(pid + " " + s[s.length-1]);
        }
        JBPopupFactory pop = JBPopupFactory.getInstance();
        ListPopupStep<String> listPopupStep = new BaseListPopupStep<String>("Run This Method - Choose process", strings) {

            String errorMessage = null;

            @Override
            public @NotNull String getTextFor(String value) {
                return super.getTextFor(value);
            }

            @Override
            public PopupStep doFinalStep(@Nullable Runnable runnable) {
                if(errorMessage != null){
                    Messages.showErrorDialog(errorMessage, "Run This Method");
                }
                return super.doFinalStep(runnable);
            }

            @Override
            public @Nullable PopupStep onChosen(String selectedValue, boolean finalChoice) {
                try {
                    String pid = selectedValue.split(" ")[0];
                    Editor editor = PlatformDataKeys.EDITOR.getData(e.getDataContext());
                    CaretModel caretModel = editor.getCaretModel();
                    Document document = editor.getDocument();
                    PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
                    PsiElement psiElement = e.getData(CommonDataKeys.PSI_ELEMENT);
                    PsiMethodImpl methodElement = (PsiMethodImpl) PsiTreeUtil.findFirstParent(psiElement, new Condition<PsiElement>() {
                        @Override
                        public boolean value(PsiElement psiElement) {
                            return psiElement instanceof PsiMethod;
                        }
                    });
                    JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(project);
                    String psiMethodName = methodElement.getName();
                    String qualifiedName = ((PsiMethodImpl) methodElement).getContainingClass().getQualifiedName();
                    PsiJavaFile containingFile = (PsiJavaFile) methodElement.getContainingFile();
                    String runComand = qualifiedName + "#" + psiMethodName;
                    @NotNull PsiParameter[] parameters = ((PsiMethodImpl) methodElement).getParameterList().getParameters();
                    if(parameters.length > 0){
                        runComand += "(";
                        for (PsiParameter parameter : parameters) {
                            runComand += parameter.getType().getCanonicalText(false).replaceAll("<.+>", "") + ",";
                        }
                        runComand = runComand.substring(0, runComand.length() - 1) + ")";
                    }
                    //PsiElement elementAt = containingFile.findElementAt(caretModel.getOffset());

                    System.out.println("[RunThisMethod]" + runComand);

                    @NotNull PsiFile[] contextHolders = FilenameIndex.getFilesByName(project, "ContextHolder.java", GlobalSearchScope.projectScope(project));
                    if(contextHolders.length == 0){
                        Messages.showErrorDialog("[RunThisMethod]请先在本项目添加一个ContextHolder.java:@Component\n" +
                                "public class ContextHolder implements ApplicationContextAware {\n" +
                                "\tprivate static ApplicationContext applicationContext;\n" +
                                "\t@Override\n" +
                                "\tpublic void setApplicationContext(ApplicationContext applicationContext) throws BeansException {\n" +
                                "\t\tContextHolder.applicationContext = applicationContext;\n" +
                                "\t}\n" +
                                "\n" +
                                "\tpublic static ApplicationContext getContext(){\n" +
                                "\t\treturn applicationContext;\n" +
                                "\t}\n" +
                                "}\n", "RunThisMethod");
                        return super.onChosen(selectedValue, finalChoice);
                    }
                    String contextHolder = Arrays.asList(contextHolders).stream().map(e->((PsiJavaFile)e).getClasses()[0].getQualifiedName()).collect(Collectors.joining(","));
                    System.out.println("[ContextHolder]" + contextHolder);
                    HintManager.getInstance().showInformationHint(e.getData(CommonDataKeys.EDITOR), "[RunThisMethod]ContextHolder:" + contextHolder + ",runMethod:" + runComand);
                    AgentRunner.run( pid, contextHolder, runComand);
                } catch (Exception exception) {
                    exception.printStackTrace();
                    errorMessage = "ERROR:" + exception.getMessage();
                }
                return super.onChosen(selectedValue, finalChoice);
            }
        };
        ListPopup listPopup = pop.createListPopup(listPopupStep);
        Component focusedComponent = WindowManagerEx.getInstanceEx().getFocusedComponent(project);
        listPopup.showInCenterOf(e.getInputEvent().getComponent());
    }
}
