package com.nicholasburczyk.packupdater.ui;

import com.nicholasburczyk.packupdater.config.ConfigManager;

import javax.swing.SwingWorker;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.Window;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class Choosers {

    private static final String OS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    private static final boolean WINDOWS = OS.contains("win");
    private static final boolean MAC = OS.contains("mac");

    private static final String WINDOWS_PICKER = """
            Add-Type -Language CSharp @'
            using System;
            using System.Runtime.InteropServices;

            public static class NativeFolderPicker
            {
                [ComImport, Guid("DC1C5A9C-E88A-4dde-A5A1-60F82A20AEF7")]
                private class FileOpenDialogRCW { }

                [ComImport, Guid("42f85136-db7e-439c-85f1-e4075d135fc8"),
                 InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
                private interface IFileDialog
                {
                    [PreserveSig] int Show(IntPtr parent);
                    void SetFileTypes(uint cFileTypes, IntPtr rgFilterSpec);
                    void SetFileTypeIndex(uint iFileType);
                    void GetFileTypeIndex(out uint piFileType);
                    void Advise(IntPtr pfde, out uint pdwCookie);
                    void Unadvise(uint dwCookie);
                    void SetOptions(uint fos);
                    void GetOptions(out uint fos);
                    void SetDefaultFolder(IShellItem psi);
                    void SetFolder(IShellItem psi);
                    void GetFolder(out IShellItem ppsi);
                    void GetCurrentSelection(out IShellItem ppsi);
                    void SetFileName([MarshalAs(UnmanagedType.LPWStr)] string pszName);
                    void GetFileName([MarshalAs(UnmanagedType.LPWStr)] out string pszName);
                    void SetTitle([MarshalAs(UnmanagedType.LPWStr)] string pszTitle);
                    void SetOkButtonLabel([MarshalAs(UnmanagedType.LPWStr)] string pszText);
                    void SetFileNameLabel([MarshalAs(UnmanagedType.LPWStr)] string pszLabel);
                    void GetResult(out IShellItem ppsi);
                    void AddPlace(IShellItem psi, int alignment);
                    void SetDefaultExtension([MarshalAs(UnmanagedType.LPWStr)] string pszDefaultExtension);
                    void Close([MarshalAs(UnmanagedType.Error)] int hr);
                    void SetClientGuid(ref Guid guid);
                    void ClearClientData();
                    void SetFilter(IntPtr pFilter);
                }

                [ComImport, Guid("43826d1e-e718-42ee-bc55-a1e261c37bfe"),
                 InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
                private interface IShellItem
                {
                    void BindToHandler(IntPtr pbc, ref Guid bhid, ref Guid riid, out IntPtr ppv);
                    void GetParent(out IShellItem ppsi);
                    void GetDisplayName(uint sigdnName, [MarshalAs(UnmanagedType.LPWStr)] out string ppszName);
                    void GetAttributes(uint sfgaoMask, out uint psfgaoAttribs);
                    void Compare(IShellItem psi, uint hint, out int piOrder);
                }

                [DllImport("shell32.dll", CharSet = CharSet.Unicode, PreserveSig = false)]
                private static extern void SHCreateItemFromParsingName(
                    [MarshalAs(UnmanagedType.LPWStr)] string pszPath, IntPtr pbc,
                    ref Guid riid, [MarshalAs(UnmanagedType.Interface)] out IShellItem ppv);

                private const uint FOS_PICKFOLDERS = 0x00000020;
                private const uint FOS_FORCEFILESYSTEM = 0x00000040;
                private const uint SIGDN_FILESYSPATH = 0x80058000;

                public static string Pick(string title, string startPath)
                {
                    IFileDialog dialog = (IFileDialog) new FileOpenDialogRCW();
                    uint options;
                    dialog.GetOptions(out options);
                    dialog.SetOptions(options | FOS_PICKFOLDERS | FOS_FORCEFILESYSTEM);
                    if (!string.IsNullOrEmpty(title)) { dialog.SetTitle(title); }

                    if (!string.IsNullOrEmpty(startPath))
                    {
                        try
                        {
                            Guid shellItemGuid = new Guid("43826d1e-e718-42ee-bc55-a1e261c37bfe");
                            IShellItem start;
                            SHCreateItemFromParsingName(startPath, IntPtr.Zero, ref shellItemGuid, out start);
                            dialog.SetFolder(start);
                        }
                        catch { }
                    }

                    int hr = dialog.Show(IntPtr.Zero);
                    if (hr != 0) { return "CANCELLED"; }

                    IShellItem result;
                    dialog.GetResult(out result);
                    string path;
                    result.GetDisplayName(SIGDN_FILESYSPATH, out path);
                    return path;
                }
            }
            '@
            [Console]::Out.Write([NativeFolderPicker]::Pick($env:PICKER_TITLE, $env:PICKER_START))
            """;

    private Choosers() {
    }

    public static Path instancesRoot() {
        String configured = ConfigManager.getInstance().getConfig().getCurseforge_path();
        if (configured != null && !configured.isBlank()) {
            Path path = Path.of(configured);
            if (Files.isDirectory(path)) {
                return path.toAbsolutePath().normalize();
            }
        }
        return null;
    }

    public static Path startDirectory() {
        Path instances = instancesRoot();
        return instances != null ? instances : Path.of(System.getProperty("user.home"));
    }

    public static void chooseDirectory(Window parent, String title, Path start, Consumer<Path> onPicked) {
        Path from = start != null && Files.isDirectory(start) ? start : startDirectory();

        if (MAC) {
            onPicked.accept(macDirectory(parent, title, from));
            return;
        }
        if (WINDOWS) {
            new SwingWorker<Result, Void>() {
                @Override
                protected Result doInBackground() {
                    return windowsDirectory(title, from);
                }

                @Override
                protected void done() {
                    Result result;
                    try {
                        result = get();
                    } catch (Exception e) {
                        result = Result.failure(e.getMessage());
                    }
                    if (result.failed()) {
                        com.nicholasburczyk.packupdater.ui.dialog.Dialogs.error(parent,
                                "Could not open the folder picker", result.error());
                        return;
                    }
                    onPicked.accept(result.path());
                }
            }.execute();
            return;
        }
        onPicked.accept(macDirectory(parent, title, from));
    }

    private record Result(Path path, String error) {

        static Result cancelled() {
            return new Result(null, null);
        }

        static Result picked(Path path) {
            return new Result(path, null);
        }

        static Result failure(String error) {
            return new Result(null, error == null ? "Unknown error" : error);
        }

        boolean failed() {
            return error != null;
        }
    }

    private static Path macDirectory(Window parent, String title, Path start) {
        String previous = System.getProperty("apple.awt.fileDialogForDirectories");
        System.setProperty("apple.awt.fileDialogForDirectories", "true");
        try {
            Frame owner = parent instanceof Frame frame ? frame : null;
            FileDialog dialog = new FileDialog(owner, title, FileDialog.LOAD);
            dialog.setDirectory(start.toString());
            dialog.setVisible(true);
            String directory = dialog.getDirectory();
            String file = dialog.getFile();
            if (file == null) {
                return null;
            }
            return Path.of(directory == null ? "" : directory, file).toAbsolutePath().normalize();
        } finally {
            if (previous == null) {
                System.clearProperty("apple.awt.fileDialogForDirectories");
            } else {
                System.setProperty("apple.awt.fileDialogForDirectories", previous);
            }
        }
    }

    private static Result windowsDirectory(String title, Path start) {
        Path script = null;
        try {
            script = Files.createTempFile("packupdater-picker", ".ps1");
            Files.writeString(script, WINDOWS_PICKER, StandardCharsets.UTF_8);

            ProcessBuilder builder = new ProcessBuilder(List.of(
                    "powershell.exe", "-NoProfile", "-STA", "-ExecutionPolicy", "Bypass",
                    "-File", script.toAbsolutePath().toString()));
            builder.environment().put("PICKER_TITLE", title == null ? "Select a folder" : title);
            builder.environment().put("PICKER_START", start.toString());

            Process process = builder.start();

            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.lines().reduce("", String::concat).trim();
            }
            String errors;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                errors = reader.lines().reduce("", (a, b) -> a + "\n" + b).trim();
            }

            if (!process.waitFor(10, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                return Result.failure("The folder picker did not respond.");
            }
            if ("CANCELLED".equals(output)) {
                return Result.cancelled();
            }
            if (output.isBlank()) {
                return Result.failure(errors.isBlank() ? "The folder picker returned nothing." : errors);
            }

            Path picked = Path.of(output).toAbsolutePath().normalize();
            if (!Files.isDirectory(picked)) {
                return Result.failure("That is not a folder: " + picked);
            }
            return Result.picked(picked);
        } catch (Exception e) {
            return Result.failure(e.getMessage());
        } finally {
            if (script != null) {
                try {
                    Files.deleteIfExists(script);
                } catch (Exception ignored) {
                    script.toFile().deleteOnExit();
                }
            }
        }
    }
}
