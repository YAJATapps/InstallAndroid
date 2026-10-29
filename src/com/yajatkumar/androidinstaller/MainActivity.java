package com.yajatkumar.androidinstaller;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private TextView titleView;
    private TextView infoView;
    private TextView logView;
    private ProgressBar progressBar;
    private Button installButton;
    private ScrollView scrollView;
    private Handler handler;

    private String bootDisk = "";
    private List<String> internalDisks = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handler = new Handler(Looper.getMainLooper());

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 48, 48, 48);
        layout.setBackgroundColor(Color.parseColor("#F5F7FA"));

        titleView = new TextView(this);
        titleView.setText("Install LineageOS to Internal Storage");
        titleView.setTextSize(22);
        titleView.setTextColor(Color.parseColor("#1A202C"));
        titleView.setTypeface(null, Typeface.BOLD);
        layout.addView(titleView);

        infoView = new TextView(this);
        infoView.setTextSize(14);
        infoView.setTextColor(Color.parseColor("#4A5568"));
        infoView.setPadding(0, 16, 0, 24);
        layout.addView(infoView);

        installButton = new Button(this);
        installButton.setText("Start Installation");
        installButton.setBackgroundColor(Color.parseColor("#2B6CB0"));
        installButton.setTextColor(Color.WHITE);
        installButton.setTextSize(16);
        installButton.setTypeface(null, Typeface.BOLD);
        installButton.setPadding(32, 24, 32, 24);
        installButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmInstallation();
            }
        });
        layout.addView(installButton);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(false);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        progressBar.setVisibility(View.GONE);
        progressBar.setPadding(0, 24, 0, 24);
        layout.addView(progressBar);

        scrollView = new ScrollView(this);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
        scrollParams.setMargins(0, 32, 0, 0);
        scrollView.setLayoutParams(scrollParams);
        scrollView.setBackgroundColor(Color.parseColor("#1A202C"));
        scrollView.setPadding(24, 24, 24, 24);

        logView = new TextView(this);
        logView.setTextSize(12);
        logView.setTextColor(Color.parseColor("#E2E8F0"));
        logView.setTypeface(Typeface.MONOSPACE);
        scrollView.addView(logView);

        layout.addView(scrollView);
        setContentView(layout);

        scanDrives();
    }

    private void appendLog(final String text) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                logView.append(text + "\n");
                scrollView.post(new Runnable() {
                    @Override
                    public void run() {
                        scrollView.fullScroll(View.FOCUS_DOWN);
                    }
                });
            }
        });
    }

    private void scanDrives() {
        appendLog("[*] Scanning system storage devices...");
        bootDisk = "";
        internalDisks.clear();

        // 1. Detect boot disk from /proc/mounts (/metadata or /data partition)
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/mounts"))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("\\s+");
                if (parts.length >= 2 && (parts[1].equals("/metadata") || parts[1].equals("/data"))) {
                    String dev = parts[0];
                    if (dev.startsWith("/dev/block/")) {
                        String devBase = new File(dev).getName();
                        if (devBase.startsWith("mmcblk") || devBase.startsWith("nvme")) {
                            bootDisk = devBase.replaceFirst("p[0-9]+$", "");
                        } else {
                            bootDisk = devBase.replaceFirst("[0-9]+$", "");
                        }
                        appendLog("[+] Boot source identified: /dev/block/" + bootDisk);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            appendLog("[-] Note: Could not read /proc/mounts: " + e.getMessage());
        }

        if (bootDisk.isEmpty()) {
            bootDisk = "sda";
        }

        // Check if already booted from internal storage
        boolean isInternalBoot = false;
        if (bootDisk.startsWith("mmcblk") || bootDisk.startsWith("nvme")) {
            File remFile = new File("/sys/class/block/" + bootDisk + "/removable");
            if (remFile.exists()) {
                try (BufferedReader br = new BufferedReader(new FileReader(remFile))) {
                    String val = br.readLine();
                    if (val != null && val.trim().equals("0")) {
                        isInternalBoot = true;
                    }
                } catch (Exception ignored) {}
            } else {
                isInternalBoot = true;
            }
        }

        if (isInternalBoot) {
            titleView.setText("LineageOS Already Installed");
            infoView.setText("Currently running directly from internal storage (/dev/block/" + bootDisk + ").\n"
                    + "Your Chromebook is fully installed and configured!");
            installButton.setVisibility(View.GONE);
            appendLog("[+] Running from internal storage. Installation not required.");
            return;
        }

        // 2. Enumerate potential target internal storage
        for (String cand : new String[]{"mmcblk1", "mmcblk0", "nvme0n1"}) {
            File candDir = new File("/sys/class/block/" + cand);
            if (candDir.exists() && !bootDisk.equals(cand)) {
                internalDisks.add(cand);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Boot Source (Live USB): /dev/block/").append(bootDisk).append("\n");
        sb.append("Target Internal Storage: ");
        if (internalDisks.isEmpty()) {
            sb.append("None detected!\n");
            installButton.setEnabled(false);
            installButton.setText("No Internal Storage Found");
            appendLog("[-] Warning: No internal eMMC or NVMe storage found.");
        } else {
            for (String d : internalDisks) {
                long sizeGb = getDiskSizeGb(d);
                sb.append("/dev/block/").append(d);
                if (sizeGb > 0) {
                    sb.append(" (~").append(sizeGb).append(" GB)");
                }
                sb.append(" ");
            }
            sb.append("\n");
            appendLog("[+] Detected internal storage target: /dev/block/" + internalDisks.get(0));
        }
        infoView.setText(sb.toString());
    }

    private long getDiskSizeGb(String diskName) {
        try (BufferedReader br = new BufferedReader(new FileReader("/sys/class/block/" + diskName + "/size"))) {
            String line = br.readLine();
            if (line != null) {
                long sectors = Long.parseLong(line.trim());
                return (sectors * 512L) / (1024L * 1024L * 1024L);
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private void confirmInstallation() {
        if (internalDisks.isEmpty()) return;
        final String targetDisk = internalDisks.get(0);
        long targetGb = getDiskSizeGb(targetDisk);
        String sizeStr = targetGb > 0 ? " (~" + targetGb + " GB)" : "";

        new AlertDialog.Builder(this)
                .setTitle("Confirm Disk Erasure")
                .setMessage("Are you sure you want to install LineageOS to /dev/block/" + targetDisk + sizeStr + "?\n\n"
                        + "WARNING: This will PERMANENTLY ERASE all data on internal storage.\n"
                        + "ChromeOS Depthcharge partitions and Android userdata will be created.")
                .setPositiveButton("Erase & Install", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        startInstallation(targetDisk);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startInstallation(final String target) {
        installButton.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setProgress(0);
        appendLog("\n=======================================================");
        appendLog(" [*] Starting LineageOS Installation to /dev/block/" + target);
        appendLog("=======================================================\n");

        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean success = runInstallerService(target);
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        progressBar.setVisibility(View.GONE);
                        if (success) {
                            showRebootDialog();
                        } else {
                            installButton.setEnabled(true);
                            appendLog("\n[-] Installation encountered errors. See logs above.");
                        }
                    }
                });
            }
        }).start();
    }

    private boolean runInstallerService(String target) {
        try {
            // Pass target drive via system property (avoids filesystem permission issues)
            appendLog("[*] Setting target drive: " + target);
            setSystemProperty("vendor.installer.target", target);

            // Reset state
            setSystemProperty("vendor.installer.state", "starting");
            setSystemProperty("vendor.installer.percent", "0");

            // Launch backend installer daemon via init property
            appendLog("[*] Launching installer daemon...");
            setSystemProperty("vendor.installer.action", "start");
            try {
                Runtime.getRuntime().exec(new String[]{"setprop", "ctl.start", "install_to_emmc"});
            } catch (Exception ignored) {}

            long lastPos = 0;
            boolean finished = false;
            boolean success = false;
            int timeoutSecs = 900; // 15 minute timeout
            long startTime = System.currentTimeMillis();

            // Log file is created by root daemon in /metadata/install.log or /data/local/tmp/install.log
            File logFile = new File("/metadata/install.log");

            while (!finished) {
                Thread.sleep(500);

                if (!logFile.exists() || !logFile.canRead()) {
                    File alt = new File("/data/local/tmp/install.log");
                    if (alt.exists() && alt.canRead()) {
                        logFile = alt;
                    }
                }

                // Check timeout
                if ((System.currentTimeMillis() - startTime) > (timeoutSecs * 1000L)) {
                    appendLog("[-] ERROR: Installation timed out after 15 minutes.");
                    break;
                }

                // Stream logs
                if (logFile.exists() && logFile.canRead()) {
                    try (RandomAccessFile raf = new RandomAccessFile(logFile, "r")) {
                        if (raf.length() > lastPos) {
                            raf.seek(lastPos);
                            String line;
                            while ((line = raf.readLine()) != null) {
                                appendLog(line);
                            }
                            lastPos = raf.getFilePointer();
                        }
                    } catch (Exception ignored) {}
                }

                // Read progress properties
                String state = getSystemProperty("vendor.installer.state");
                String percentStr = getSystemProperty("vendor.installer.percent");
                if (!percentStr.isEmpty()) {
                    try {
                        final int p = Integer.parseInt(percentStr);
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                progressBar.setProgress(p);
                            }
                        });
                    } catch (Exception ignored) {}
                }

                if ("success".equalsIgnoreCase(state)) {
                    finished = true;
                    success = true;
                } else if ("failed".equalsIgnoreCase(state) || "error".equalsIgnoreCase(state)) {
                    finished = true;
                    success = false;
                }
            }

            // Read any final lines remaining in the log file
            if (logFile.exists() && logFile.canRead()) {
                try (RandomAccessFile raf = new RandomAccessFile(logFile, "r")) {
                    if (raf.length() > lastPos) {
                        raf.seek(lastPos);
                        String line;
                        while ((line = raf.readLine()) != null) {
                            appendLog(line);
                        }
                    }
                } catch (Exception ignored) {}
            }

            return success;
        } catch (Exception e) {
            appendLog("[-] Installer exception: " + e.getMessage());
            return false;
        }
    }

    private void setSystemProperty(String key, String value) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"setprop", key, value});
            p.waitFor();
        } catch (Exception e) {
            appendLog("[-] Warning: Failed to set " + key + ": " + e.getMessage());
        }
    }

    private String getSystemProperty(String key) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"getprop", key});
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String val = br.readLine();
            br.close();
            p.waitFor();
            return val != null ? val.trim() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private void showRebootDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Installation Complete!")
                .setMessage("LineageOS has been successfully installed to your internal storage.\n\n"
                        + "Please remove your USB flash drive and tap Reboot Now.")
                .setCancelable(false)
                .setPositiveButton("Reboot Now", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            Runtime.getRuntime().exec(new String[]{"svc", "power", "reboot"});
                        } catch (Exception e) {
                            try {
                                Runtime.getRuntime().exec(new String[]{"reboot"});
                            } catch (Exception ignored) {}
                        }
                    }
                })
                .show();
    }
}
