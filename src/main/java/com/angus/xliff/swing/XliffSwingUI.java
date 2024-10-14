package com.angus.xliff.swing;

import com.angus.xliff.convert.XmlSourceExtractor;
import com.angus.xliff.convert.ChatGPTClient;
import com.angus.xliff.convert.UpdateXliffTarget;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XliffSwingUI extends JFrame {
	
	private static final long serialVersionUID = 1L;
	
    private JTextField filePathField;
    private JTextArea logArea;
    private transient ResourceBundle messages;
    private transient Preferences prefs;
    private static final String PREF_XLIFF_PATH = "xliffPath";

    // 日誌記錄器
    private static final Logger logger = LoggerFactory.getLogger(XliffSwingUI.class);

    // STEP 按鈕
    private JButton step1Button;
    private JButton step2Button;
    private JButton step3Button;

    public XliffSwingUI() {

        Locale locale = Locale.getDefault();
        messages = ResourceBundle.getBundle("MessagesBundle", locale);
        prefs = Preferences.userNodeForPackage(XliffSwingUI.class);

        setTitle(this.getMessage("title"));
        setSize(700, 600);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10)); // 增加水平和垂直間距

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            log(getMessage("error_occurred") + e.getMessage());
        }

        // 主面板，使用邊框布局並添加內邊距
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        add(mainPanel, BorderLayout.CENTER);

        // 文件選擇面板
        JPanel filePanel = new JPanel(new BorderLayout(5, 5));
        JLabel fileLabel = new JLabel(getMessage("file_path"));
        filePathField = new JTextField();
        JButton fileButton = new JButton(getMessage("select_file"));
        fileButton.addActionListener(new FileButtonListener());

        filePanel.add(fileLabel, BorderLayout.WEST);
        filePanel.add(filePathField, BorderLayout.CENTER);
        filePanel.add(fileButton, BorderLayout.EAST);
        mainPanel.add(filePanel, BorderLayout.NORTH);

        // 讀取暫存的路徑
        String cachedPath = prefs.get(PREF_XLIFF_PATH, "");
        if (!cachedPath.isEmpty()) {
            File cachedFile = new File(cachedPath);
            if (cachedFile.exists()) {
                filePathField.setText(cachedFile.getAbsolutePath());
            }
        }

        // 按鈕面板
        JPanel buttonPanel = new JPanel(new GridLayout(1, 3, 10, 0)); // 水平方向間距
        step1Button = new JButton(getMessage("step_1"));
        step2Button = new JButton(getMessage("step_2"));
        step3Button = new JButton(getMessage("step_3"));

        step1Button.addActionListener(new StepButtonListener(1));
        step2Button.addActionListener(new StepButtonListener(2));
        step3Button.addActionListener(new StepButtonListener(3));

        buttonPanel.add(step1Button);
        buttonPanel.add(step2Button);
        buttonPanel.add(step3Button);
        mainPanel.add(buttonPanel, BorderLayout.CENTER);

        // 日誌區域
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setPreferredSize(new Dimension(600, 200));
        mainPanel.add(scrollPane, BorderLayout.SOUTH);
    }

    // 輔助日誌方法，同時將消息寫入 logArea 和 logger
    private void log(String message) {
        // 在 EDT 上更新 UI
        SwingUtilities.invokeLater(() -> logArea.append(message + "\n"));
        logger.info(message);
    }

    private class FileButtonListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            JFileChooser fileChooser = new JFileChooser();
            int result = fileChooser.showOpenDialog(XliffSwingUI.this);
            if (result == JFileChooser.APPROVE_OPTION) {
                File selectedFile = fileChooser.getSelectedFile();
                filePathField.setText(selectedFile.getAbsolutePath());
                // 暫存選擇的路徑
                prefs.put(PREF_XLIFF_PATH, selectedFile.getAbsolutePath());
                log(getMessage("file_selected") + selectedFile.getAbsolutePath());
            }
        }
    }

    private class StepButtonListener implements ActionListener {
        private int step;

        public StepButtonListener(int step) {
            this.step = step;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            disableStepButtons();
            String filePath = filePathField.getText().trim();
            if (filePath.isEmpty()) {
                log(getMessage("select_file_first"));
                enableStepButtons();
                return;
            }

            File file = new File(filePath);
            if (!file.exists()) {
                log(getMessage("file_not_found"));
                enableStepButtons();
                return;
            }

            switch (step) {
                case 1:
                    executeStep1(filePath);
                    break;
                case 2:
                    executeStep2(filePath);
                    break;
                case 3:
                    executeStep3(filePath);
                    break;
                default:
                    log(getMessage("unknown_step"));
                    enableStepButtons();
            }
        }

        private void disableStepButtons() {
            step1Button.setEnabled(false);
            step2Button.setEnabled(false);
            step3Button.setEnabled(false);
        }

        private void enableStepButtons() {
            step1Button.setEnabled(true);
            step2Button.setEnabled(true);
            step3Button.setEnabled(true);
        }

        private void executeStep1(String filePath) {
            SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
                @Override
                protected Void doInBackground() {
                    publish(getMessage("step_1_start"));
                    try {
                        XmlSourceExtractor.main(new String[]{filePath});
                        publish(getMessage("step_1_complete") + filePath);
                        // 假設 XmlSourceExtractor2 返回抽出的筆數
                        int extractedCount = XmlSourceExtractor.getExtractedCount();
                        publish(getMessage("step_1_extracted_count") + extractedCount);
                    } catch (Exception ex) {
                    	publish(getMessage("warn_xml_support_version"));
                        publish(getMessage("error_occurred") + ex.getMessage());
                        logger.error("Step 1 error:" + ex.getMessage(), ex);
                    }
                    return null;
                }

                @Override
                protected void process(java.util.List<String> chunks) {
                    for (String message : chunks) {
                        log(message);
                    }
                }

                @Override
                protected void done() {
                    enableStepButtons();
                }
            };
            worker.execute();
        }

        private void executeStep2(String filePath) {
            SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
                @Override
                protected Void doInBackground() {
                    publish(getMessage("step_2_start"));
                    int totalLines;
                    try {
                        totalLines = ChatGPTClient.getTotalLines(filePath);
                        publish(getMessage("step_2_sending_request"));
                    } catch (Exception ex) {
                        publish(getMessage("error_occurred") + ex.getMessage());
                        logger.error("Step 2 initial error: " + ex.getMessage(), ex);
                        return null;
                    }

                    int completedLines = 0;
                    while (completedLines < totalLines) {
                        try {
                            int processed = ChatGPTClient.processNextBatch(filePath);
                            completedLines += processed;
                            publish(completedLines + "/" + totalLines + getMessage("step_2_completed"));
                        } catch (NullPointerException npe) {
                            publish(getMessage("error_null_response"));
                            logger.error("Null response in Step 2:" + npe.getMessage(), npe);
                            break;
                        } catch (IllegalArgumentException iae) {
                            publish(getMessage("error_invalid_key"));
                            logger.error("Invalid key in Step 2:" + iae.getMessage(), iae);
                            break;
                        } catch (Exception ex) {
                            String message = ex.getMessage();
                            if (message.contains("rate limit")) {
                                publish(getMessage("error_rate_limit"));
                            } else if (message.contains("network")) {
                                publish(getMessage("error_network"));
                            } else if (message.contains("timeout")) {
                                publish(getMessage("error_timeout"));
                            } else if (message.contains("invalid_request_error")) {
                                publish(getMessage("error_invalid_key"));
                            } else {
                                publish(getMessage("error_occurred") + message);
                            }
                            logger.error("Step 2 processing error:" + ex.getMessage(), ex);
                            break;
                        }
                    }

                    if (completedLines == totalLines) {
                        publish(getMessage("step_2_complete"));
                    } else {
                        publish(getMessage("step_2_failed"));
                    }

                    return null;
                }

                @Override
                protected void process(java.util.List<String> chunks) {
                    for (String message : chunks) {
                        log(message);
                    }
                }

                @Override
                protected void done() {
                    enableStepButtons();
                }
            };

            worker.execute();
        }

        private void executeStep3(String filePath) {
            SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
                @Override
                protected Void doInBackground() {
                    publish(getMessage("step_3_start"));
                    try {
                        UpdateXliffTarget.main(new String[]{filePath});
                        int updatedCount = UpdateXliffTarget.getUpdatedCount();
                        String updatedFilePath = UpdateXliffTarget.getUpdatedFilePath();
                        if (!updatedFilePath.isEmpty()) {
                            publish(getMessage("step_3_complete") + updatedCount + " " 
                                   + getMessage("step_3_to_path") + updatedFilePath);
                        } else {
                            publish(getMessage("step_3_complete") + updatedCount + " " 
                                   + getMessage("step_3_to_path") + "未知路徑");
                        }
                    } catch (Exception ex) {
                    	publish(getMessage("warn_xml_support_version"));
                        publish(getMessage("error_occurred") + ex.getMessage());
                        logger.error("Step 3 error:" + ex.getMessage(), ex);
                    }
                    return null;
                }

                @Override
                protected void process(java.util.List<String> chunks) {
                    for (String message : chunks) {
                        log(message);
                    }
                }

                @Override
                protected void done() {
                    enableStepButtons();
                }
            };
            worker.execute();
        }
    }

    private String getMessage(String key) {
        if (messages != null && messages.containsKey(key)) {
            return messages.getString(key);
        } else {
            return key;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            XliffSwingUI frame = new XliffSwingUI();
            frame.setVisible(true);
        });
    }
}
