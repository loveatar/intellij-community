// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.completion.DumbModeNotifier;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.ide.lightEdit.intentions.openInProject.LightEditOpenInProjectIntention;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.FileSaverDialog;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.ApiStatus;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class LightEditUtil {
  private static final String ENABLED_FILE_OPEN_KEY = "light.edit.file.open.enabled";

  private LightEditUtil() {
  }

  public static boolean openFile(@NotNull Path path) {
    VirtualFile virtualFile = VfsUtil.findFile(path, true);
    if (virtualFile != null) {
      return LightEdit.openFile(virtualFile);
    }
    return false;
  }

  public static @NotNull Project getProject() {
    return LightEditService.getInstance().getOrCreateProject();
  }

  @Nullable
  public static Project getProjectIfCreated() {
    return LightEditService.getInstance().getProject();
  }

  static boolean confirmClose(@NotNull String message,
                              @NotNull String title,
                              @NotNull Runnable saveRunnable) {
    final String[] options = {getCloseSave(), getCloseDiscard(), getCloseCancel()};
    int result = Messages.showDialog(getProject(), message, title, options, 0, Messages.getWarningIcon());
    if (result >= 0) {
      if (getCloseCancel().equals(options[result])) {
        return false;
      }
      else if (getCloseSave().equals(options[result])) {
        saveRunnable.run();
      }
      return true;
    }
    else {
      return false;
    }
  }

  @Nullable
  static VirtualFile chooseTargetFile(@NotNull Component parent, @NotNull LightEditorInfo editorInfo) {
    FileSaverDialog saver =
      FileChooserFactory.getInstance()
        .createSaveFileDialog(new FileSaverDescriptor(
          "Save as",
          "Choose a target file",
          getKnownExtensions()),parent);
    VirtualFileWrapper fileWrapper = saver.save(VfsUtil.getUserHomeDir(), editorInfo.getFile().getPresentableName());
    if (fileWrapper != null) {
      return fileWrapper.getVirtualFile(true);
    }
    return null;
  }

  private static String[] getKnownExtensions() {
    return
      ArrayUtil.toStringArray(
        Stream.of(FileTypeManager.getInstance().getRegisteredFileTypes())
          .map(fileType -> fileType.getDefaultExtension()).sorted().distinct().collect(Collectors.toList()));
  }

  private static String getCloseSave() {
    return ApplicationBundle.message("light.edit.close.save");
  }

  private static String getCloseDiscard() {
    return ApplicationBundle.message("light.edit.close.discard");
  }

  private static String getCloseCancel() {
    return ApplicationBundle.message("light.edit.close.cancel");
  }

  public static void markUnknownFileTypeAsPlainTextIfNeeded(@Nullable Project project, @NotNull VirtualFile file) {
    if (project != null && !project.isDefault() && !LightEdit.owns(project)) {
      return;
    }
    if (isLightEditEnabled()) {
      LightEditFileTypeOverrider.markUnknownFileTypeAsPlainText(file);
    }
  }

  public static boolean isLightEditEnabled() {
    return Registry.is(ENABLED_FILE_OPEN_KEY) && !PlatformUtils.isDataGrip();
  }

  @ApiStatus.Internal
  @NotNull
  public static DumbModeNotifier createDumbModeCompletionNotifier() {
    return new DumbModeNotifier() {
      @Override
      public String getIncompleteMessageSuffix() {
        return CodeInsightBundle.message("completion.incomplete.light.edit.suffix");
      }

      @Override
      public void showIncompleteHint(@NotNull Editor editor, @NotNull String text) {
        HintManager.getInstance().showInformationHint(
          editor, escapeXmlWithoutLink(text),
          new HyperlinkListener() {
            @Override
            public void hyperlinkUpdate(HyperlinkEvent e) {
              VirtualFile file = LightEditService.getInstance().getSelectedFile();
              if (file != null) {
                LightEditOpenInProjectIntention.performOn(file);
              }
            }
          });
      }

      private String escapeXmlWithoutLink(@NotNull String text) {
        int linkPos = text.indexOf("<a href=");
        if (linkPos < 0) linkPos = text.length();
        return StringUtil.escapeXmlEntities(text.substring(0, linkPos)) + text.substring(linkPos);
      }
    };
  }
}
