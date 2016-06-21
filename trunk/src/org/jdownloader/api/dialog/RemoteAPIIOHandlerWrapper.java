package org.jdownloader.api.dialog;

import java.awt.Dialog.ModalityType;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

import javax.swing.Icon;
import javax.swing.SwingUtilities;

import org.appwork.exceptions.WTFException;
import org.appwork.resources.AWUTheme;
import org.appwork.uio.CloseReason;
import org.appwork.uio.ConfirmDialogInterface;
import org.appwork.uio.ExceptionDialogInterface;
import org.appwork.uio.MessageDialogInterface;
import org.appwork.uio.UIOManager;
import org.appwork.uio.UserIODefinition;
import org.appwork.uio.UserIOHandlerInterface;
import org.appwork.utils.StringUtils;
import org.appwork.utils.locale._AWU;
import org.appwork.utils.logging2.LogSource;
import org.appwork.utils.logging2.extmanager.LoggerFactory;
import org.appwork.utils.swing.EDTRunner;
import org.appwork.utils.swing.dialog.AbstractDialog;
import org.appwork.utils.swing.dialog.ConfirmDialog;
import org.appwork.utils.swing.dialog.Dialog;
import org.appwork.utils.swing.dialog.DialogCanceledException;
import org.appwork.utils.swing.dialog.DialogClosedException;
import org.appwork.utils.swing.dialog.ExceptionDialog;
import org.appwork.utils.swing.dialog.MessageDialogImpl;
import org.jdownloader.logging.LogController;
import org.jdownloader.settings.SilentModeSettings.DialogDuringSilentModeAction;
import org.jdownloader.settings.staticreferences.CFG_SILENTMODE;

import jd.gui.swing.jdgui.JDGui;

public class RemoteAPIIOHandlerWrapper implements UserIOHandlerInterface {

    private final DialogApiImpl remoteHandler;
    private final LogSource     logger;

    public RemoteAPIIOHandlerWrapper(UserIOHandlerInterface i) {
        remoteHandler = new DialogApiImpl(this);
        logger = LogController.getInstance().getLogger(RemoteAPIIOHandlerWrapper.class.getName());
    }

    @Override
    public boolean showConfirmDialog(int flags, String title, String message, Icon icon, String ok, String cancel, final String dontShowAgainKey) {
        final ConfirmDialog dialog;
        if (StringUtils.isEmpty(dontShowAgainKey)) {
            dialog = new ConfirmDialog(flags, title, message, icon, ok, cancel);
        } else {
            dialog = new ConfirmDialog(flags, title, message, icon, ok, cancel) {
                @Override
                public String getDontShowAgainKey() {
                    return dontShowAgainKey;
                }
            };
        }
        final ConfirmDialogInterface io = show(ConfirmDialogInterface.class, dialog);
        return io.getCloseReason() == CloseReason.OK;
    }

    @Override
    public boolean showConfirmDialog(int flags, String title, String message, Icon icon, String ok, String cancel) {
        return showConfirmDialog(flags, title, message, icon, ok, cancel, null);
    }

    @Override
    public boolean showConfirmDialog(int flags, String title, String message) {
        return showConfirmDialog(flags, title, message, null, null, null, null);
    }

    @Override
    public void showMessageDialog(String message) {
        show(MessageDialogInterface.class, new MessageDialogImpl(0, message));
    }

    public <T extends UserIODefinition> T showModeless(Class<T> class1, T impl) {
        // synchronized (this) {

        if (impl instanceof AbstractDialog) {
            final AbstractDialog dialog = (AbstractDialog) impl;
            ApiHandle handle = null;
            try {
                dialog.forceDummyInit();
                try {
                    if (dialog.evaluateDontShowAgainFlag()) {

                        return impl;
                        //
                    }

                } catch (Exception e) {
                    // Dialogs are not initialized.... nullpointers are very likly
                    // These nullpointers should be fixed
                    // in this case, we should continue normal
                    logger.log(e);
                }

                boolean silentModeActive = org.appwork.utils.Application.isHeadless() ? true : JDGui.getInstance().isSilentModeActive();

                if (silentModeActive && !org.appwork.utils.Application.isHeadless()) {

                    if (CFG_SILENTMODE.ON_DIALOG_DURING_SILENT_MODE_ACTION.getValue() == DialogDuringSilentModeAction.CANCEL_DIALOG) {
                        // Cancel dialog
                        throw new DialogClosedException(Dialog.RETURN_CLOSED);
                    }
                }
                handle = remoteHandler.enqueue(class1, impl);
                //
                // if (!Application.isJared(RemoteAPIIOHandlerWrapper.class)) {
                // ((AbstractDialog<?>) impl).setTitle(((AbstractDialog<?>) impl).getTitle() + " DialogID: " + handle.getId());
                // }

                if (silentModeActive) {

                    // if this is the edt, we should not block it.. NEVER
                    if (!SwingUtilities.isEventDispatchThread()) {
                        // block dialog calls... the shall appear as soon as isSilentModeActive is false.
                        long countdown = -1;

                        if (dialog.isCountdownFlagEnabled()) {
                            long countdownDif = dialog.getCountdown();
                            countdown = System.currentTimeMillis() + countdownDif;
                        }
                        if (countdown < 0 && CFG_SILENTMODE.ON_DIALOG_DURING_SILENT_MODE_ACTION.getValue() == DialogDuringSilentModeAction.WAIT_IN_BACKGROUND_UNTIL_WINDOW_GETS_FOCUS_OR_TIMEOUT) {
                            countdown = System.currentTimeMillis() + CFG_SILENTMODE.ON_DIALOG_DURING_SILENT_MODE_ACTION_TIMEOUT.getValue();

                        }
                        if (!org.appwork.utils.Application.isHeadless()) {
                            JDGui.getInstance().flashTaskbar();
                        }
                        while (!handle.isDisposed() && (org.appwork.utils.Application.isHeadless() || JDGui.getInstance().isSilentModeActive())) {
                            if (countdown > 0) {

                                Thread.sleep(Math.min(Math.max(1, countdown - System.currentTimeMillis()), 250));
                                if (System.currentTimeMillis() > countdown) {
                                    dialog.onTimeout();
                                    // clear interrupt
                                    Thread.interrupted();

                                    return impl;

                                }
                            } else {
                                Thread.sleep(250);
                            }
                        }
                    }
                }
                if (handle != null && handle.isDisposed()) {
                    return (T) (handle.getAnswer() != null ? handle.getAnswer() : impl);
                }
                dialog.resetDummyInit();

                new EDTRunner() {

                    @Override
                    protected void runInEDT() {
                        dialog.displayDialog();

                        dialog.getDialog().addWindowListener(new WindowListener() {

                            @Override
                            public void windowOpened(WindowEvent e) {
                            }

                            @Override
                            public void windowIconified(WindowEvent e) {
                            }

                            @Override
                            public void windowDeiconified(WindowEvent e) {
                            }

                            @Override
                            public void windowDeactivated(WindowEvent e) {
                            }

                            @Override
                            public void windowClosing(WindowEvent e) {
                                synchronized (dialog) {

                                    dialog.notifyAll();
                                }

                            }

                            @Override
                            public void windowClosed(WindowEvent e) {
                                synchronized (dialog) {

                                    dialog.notifyAll();
                                }

                            }

                            @Override
                            public void windowActivated(WindowEvent e) {
                            }
                        });
                    }
                }.waitForEDT();

                while (dialog.getDialog().isDisplayable()) {
                    synchronized (dialog) {
                        dialog.wait(10000);
                    }
                }

            } catch (InterruptedException e) {
                dialog.setInterrupted();
                // throw new DialogClosedException(Dialog.RETURN_INTERRUPT, e);
                // throw new DialogClosedException(Dialog.RETURN_INTERRUPT);
            } catch (DialogClosedException e) {
                dialog.fillReturnMask(e);
            } catch (Exception e) {
                logger.log(e);

            } finally {
                try {
                    dialog.dispose();
                } catch (Exception e) {

                }
                try {
                    if (handle != null) {
                        handle.dispose();
                    }
                } catch (Exception e) {

                }
            }

            if (handle == null) {
                return impl;
            }
            return (T) (handle.getAnswer() != null ? handle.getAnswer() : impl);
        } else {
            throw new WTFException("Dialog Type not supported");
        }

    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends UserIODefinition> T show(Class<T> class1, T impl) {
        // evaluate do not show again flag
        if (impl instanceof AbstractDialog) {
            AbstractDialog<?> dialog = (AbstractDialog<?>) impl;
            if (dialog.getModalityType() == ModalityType.MODELESS) {
                if (SwingUtilities.isEventDispatchThread()) {
                    IllegalStateException e;
                    LoggerFactory.getDefaultLogger().log(e = new IllegalStateException("Cannot call a Modeless Blocking dialog in the EDT. Use a Wrapperthread"));
                    throw e;
                }
                return showModeless(class1, impl);
            }
            try {
                dialog.forceDummyInit();
                if (dialog.evaluateDontShowAgainFlag()) {
                    // final int mask = dialog.getReturnmask();
                    // if (BinaryLogic.containsSome(mask, Dialog.RETURN_CLOSED)) { throw new DialogClosedException(mask); }
                    // if (BinaryLogic.containsSome(mask, Dialog.RETURN_CANCEL)) { throw new DialogCanceledException(mask); }
                    // if (!BinaryLogic.containsSome(mask, Dialog.RETURN_OK)) { throw new DialogCanceledException(mask |
                    // Dialog.RETURN_CLOSED); }
                    return impl;
                }
                // } catch (DialogNoAnswerException e) {
                // throw e;
            } catch (Exception e) {
                // Dialogs are not initialized.... nullpointers are very likly
                // These nullpointers should be fixed
                // in this case, we should continue normal
                logger.log(e);
            }

            dialog.resetDummyInit();
        }

        final ApiHandle handle = remoteHandler.enqueue(class1, impl);
        try {
            try {
                if (impl instanceof AbstractDialog) {
                    // if (!Application.isJared(RemoteAPIIOHandlerWrapper.class)) {
                    // ((AbstractDialog<?>) impl).setTitle(((AbstractDialog<?>) impl).getTitle() + " DialogID: " + handle.getId());
                    // }
                    if (org.appwork.utils.Application.isHeadless()) {
                        if (handle != null) {
                            handle.waitFor();
                        }
                    } else {
                        Dialog.getInstance().showDialog((AbstractDialog<?>) impl);
                    }
                } else {
                    throw new WTFException("Not Supported Dialog Type!: " + impl);
                }
            } catch (final DialogClosedException e) {
                if (e.isCausedByInterrupt()) {

                }
                // no Reason to log here
            } catch (final DialogCanceledException e) {
                // no Reason to log here

            } finally {
                if (handle != null) {
                    handle.dispose();
                }
            }
            if (handle == null) {
                return impl;
            }
            final T ret = (T) (handle.getAnswer() != null ? handle.getAnswer() : impl);

            if (impl instanceof AbstractDialog) {
                final AbstractDialog<?> dialog = (AbstractDialog<?>) impl;
                if (dialog.isDontShowAgainFlagEabled()) {
                    final boolean dontShowAgain = ret.isDontShowAgainSelected();
                    final boolean clickedOK = ret.getCloseReason() == CloseReason.OK;
                    dialog.writeDontShowAgainAnswer(clickedOK, dontShowAgain);
                }

            }
            return ret;
        } catch (Exception e) {
            logger.log(e);
        }
        return impl;

    }

    @Override
    public void showErrorMessage(String message) {
        this.showConfirmDialog(UIOManager.BUTTONS_HIDE_CANCEL | Dialog.STYLE_SHOW_DO_NOT_DISPLAY_AGAIN, _AWU.T.DIALOG_ERROR_TITLE(), message, AWUTheme.I().getIcon(Dialog.ICON_ERROR, 32), null, null);
    }

    public void onHandlerDone(final ApiHandle ret) {
        if (ret.getImpl() instanceof AbstractDialog && !org.appwork.utils.Application.isHeadless()) {
            new EDTRunner() {
                @Override
                protected void runInEDT() {
                    try {
                        if (!((AbstractDialog) ret.getImpl()).isDisposed()) {
                            ((AbstractDialog) ret.getImpl()).interrupt();
                        }
                    } catch (Exception e) {

                    }
                }
            };
        }
    }

    public DialogApiImpl getRemoteHandler() {
        return remoteHandler;
    }

    @Override
    public void showException(String message, Throwable e) {
        final ExceptionDialog dialog = new ExceptionDialog(UIOManager.LOGIC_DONT_SHOW_AGAIN_DELETE_ON_EXIT | UIOManager.BUTTONS_HIDE_CANCEL, _AWU.T.DIALOG_ERROR_TITLE(), message, e, null, null);
        show(ExceptionDialogInterface.class, dialog);
    }

}
