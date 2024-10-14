# Supported Languages

Language                    | Guidelines
----------------------------|----------------
English (en)                | [README](https://github.com/Angus-repo/xliff-trans/blob/main/README.md)
Chinese Traditional (zh-TW) | [README_zh-TW](https://github.com/Angus-repo/xliff-trans/blob/main/README_zh-TW.md)
Czech (cs)                  | [README_cs](https://github.com/Angus-repo/xliff-trans/blob/main/README_cs.md)

# Xliff-trans 翻譯工具

此工具是一個支援xliff(1.2)格式，透過外部 AI 服務（如 ChatGPT）來進行 XLIFF翻譯。

### 使用說明：

#### 1. 從 Crowdin 匯出 XLIFF 文件：
此範例中，我們使用 Crowdin。從您的專案中選擇「Export in XLIFF」，然後下載文件。
![image](https://github.com/user-attachments/assets/fc8517fb-1888-4b60-a694-529269f6c037)

#### 2. 下載程式![xliff-trans release](https://github.com/Angus-repo/xliff-trans/releases/tag/v0.1.1)：

選擇適合您作業系統的文件：

##### Windows 使用者：
此程式是用 Java 撰寫的，需要 JDK1.8 或 JRE8以上版本，如果未安裝JDK8以上，請先至[java下載](https://www.oracle.com/tw/java/technologies/downloads/)

```bash
 xliff-trans-0.1.1.zip 
```

##### macOS 使用者：
如果您已安裝 Java 8 或更高版本，請下載：
```bash
 xliff-trans-x.x.x-macos.tar.gz 
```
如果您沒有 Java 8 或更高版本，或不確定，請下載包含 Java 運行時的版本：
```bash
 xliff-trans-x.x.x-macos-withJRE.tar.gz 
```

##### Linux 使用者：
此程式是用 Java 撰寫的，需要 JDK1.8 或 JRE8以上版本，如果未安裝JDK8以上，請先至[java下載](https://www.oracle.com/tw/java/technologies/downloads/)
```bash
 xliff-trans-x.x.x.tar.gz 
```
下載後請先解壓檔案。

<a id="ai-translation"></a>
#### 3. AI 翻譯設置（可選）：

##### &nbsp;&nbsp;&nbsp;&nbsp;3.1 編輯 `prefix_promp.txt`（可選）：
&nbsp;&nbsp;&nbsp;&nbsp;如果您打算使用基於 AI 的 token 翻譯，請在 `prefix_promp.txt` 中寫下您想要翻譯的語言以及指定的單字翻譯、語氣等。
> **注意**：你的翻譯品質取決於AI模型與你的promp，請將希望指定的翻譯方式完整輸入在 `prefix_promp.txt`中

##### &nbsp;&nbsp;&nbsp;&nbsp;3.2 在 `Config.properties` 中指定 OpenAI 金鑰（可選）：
&nbsp;&nbsp;&nbsp;&nbsp;輸入您的 OpenAI 金鑰 token（會產生額外費用）。請參考此影片進行設置： 
[觀看影片](https://youtu.be/lrLBq2M-GZk?t=225)

取得api_key後，請在Config.properties中輸入
```bash
api_key=sk-proj-xxxxxxxxxxxxxxxxxxxxxx
```
如果你需要修改AI模型，請在此處指定
```bash
model_version=gpt-4o-mini
```

> **注意**：你能夠使用哪些模型，取決於[Usage tiers](https://platform.openai.com/docs/guides/rate-limits/usage-tiers)，如果你已付款5美金，那你可以成為Tier1 user，[Tier1詳情請見](https://platform.openai.com/docs/guides/rate-limits/tier-1-rate-limits)


#### 4. 啟動 `xliff-trans`：
##### Windows 使用者：
運行：
```bash
run.bat
```
注意：Windows 可能會顯示安全警告。點擊「更多資訊」=>「仍要執行」。

![image](https://github.com/user-attachments/assets/d84d068e-1bec-460d-b6ef-4d92b3d51a50)

![image](https://github.com/user-attachments/assets/0b6e7bf8-c7b8-4d54-bf16-488e2a30097d)



##### macOS 使用者：
運行：
```bash
./run_macos.sh
```
或
```bash
./run_macos_withJRE.sh
```

##### Linux 使用者：
運行：
```bash
java -cp . -jar xliff-trans-0.1.1.jar
```

啟動程式後，您將看到檔案路徑選擇、操作按鈕以及底部的日誌輸出。首先，選擇您要翻譯的 XLIFF 檔案路徑（例如 `/path/xxx.xliff`）。

#### 5. 步驟 1 - 抽出待翻議的文字：
選擇 XLIFF 文件路徑並按「步驟 1」。程式將會提取帶有 `needs-transcate` 屬性的文字，並將其保存到 `/path/xxx.xliff_source_text.txt`。這將僅包含 `<source id="xxx">source text</source>`，以便讓 AI 翻譯使用。

#### 6. 步驟 2 - 開始翻譯：
##### &nbsp;&nbsp;&nbsp;&nbsp;方法 1：使用 ChatGPT 網頁介面：
從 `/path/xxx.xliff_source.txt` 中複製段落(建議100列)，並將其貼到 ChatGPT 對話中。根據您的模型，翻譯速度和準確性可能有所不同。例如，使用 GPT-4o 時，每次貼 100 行，等待結果，然後將結果保存到 `/path/xxx.xliff_target.txt`。

##### &nbsp;&nbsp;&nbsp;&nbsp;方法 2：使用 OpenAI API（[額外付費](https://openai.com/api/pricing/)）：
您需要綁定信用卡並生成 API token。
> **注意**：請確定你已輸入api_key，請參見[3. AI 翻譯設置（可選）](#ai-translation)


在 `prefix_promp.txt` 中，指定目標語言，並提及任何需要特殊翻譯規則的術語。準備好一切後，按「步驟 2」將 50 行文本一次性發送至 OpenAI，使用默認的 GPT-4o-mini。結果將儲存在 `/path/xxx.xliff_target.txt` 中。

非 `source` 標籤的資料會在「步驟 3」中被跳過，因此如果發生xml結構不正確，可以不用理會。
API 使用費用可以在 https://platform.openai.com/usage 中追蹤（更新會有幾分鐘的延遲）。

#### 7. 步驟 3 - 將翻譯合併到 XLIFF：
點擊「步驟 3」將翻譯自 `/path/xxx.xliff_target.txt` 中的文本與原始 XLIFF 文件基於 ID 進行合併，然後保存為 `/path/xxx.xliff_update.xliff`，並將target屬性改為translated
```xml
<target state="translated">xxxxxx</target>
```

#### 8. 上傳翻譯好的 XLIFF：
將 `/path/xxx.xliff_update.xliff` 上傳到 Crowdin 並檢查結果。
