package com.github.gradusnikov.eclipse.assistai.handlers;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import javax.inject.Inject;
import javax.inject.Named;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.e4.core.di.annotations.Execute;
import org.eclipse.e4.ui.services.IServiceConstants;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.ITextEditor;

import com.github.gradusnikov.eclipse.assistai.Activator;
import com.github.gradusnikov.eclipse.assistai.services.OpenAIStreamJavaHttpClient;

public class AssistAICodeRefactorHandler
{


    @Inject
    private JobFactory jobFactory;
    
    public AssistAICodeRefactorHandler()
    {
    }



    @Execute
    public void execute(@Named(IServiceConstants.ACTIVE_SHELL) Shell s)
    {

        Activator.getDefault().getLog().info("Asking AI to refactor the code");

        // Get the active editor
        IWorkbenchPage activePage = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
        IEditorPart activeEditor = activePage.getActiveEditor();

        // Check if it is a text editor
        if (activeEditor instanceof ITextEditor)
        {
            ITextEditor textEditor = (ITextEditor) activeEditor;

            // Retrieve the document and text selection
            ITextSelection textSelection = (ITextSelection) textEditor.getSelectionProvider().getSelection();

            Activator.getDefault().getLog().info("Text selection:\n" + textSelection);

            IDocument document = textEditor.getDocumentProvider().getDocument(textEditor.getEditorInput());
            String documentText = document.get();
            String selectedText = textSelection.getText();

            String fileName = activeEditor.getEditorInput().getName();

//            IJavaElement elem = JavaUI.getEditorInputJavaElement(textEditor.getEditorInput());
//            if (elem instanceof ICompilationUnit)
//            {
//                ITextSelection sel = (ITextSelection) textEditor.getSelectionProvider().getSelection();
//                IJavaElement selected;
//                try
//                {
//                    selected = ((ICompilationUnit) elem).getElementAt(sel.getOffset());
//                    if (selected != null && selected.getElementType() == IJavaElement.METHOD)
//                    {
//                        System.out.println("Selected method: " + selected);
//                    }
//                }
//                catch (JavaModelException e)
//                {
//                    // TODO Auto-generated catch block
//                    e.printStackTrace();
//                }
//            }
            Job job = jobFactory.createRefactorJob(documentText, selectedText, fileName);
            job.schedule();
        }
    }
}
