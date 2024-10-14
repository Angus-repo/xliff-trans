# Supported Languages

Language                    | Guidelines
----------------------------|----------------
English (en)                | [README](https://github.com/Angus-repo/xliff-trans/blob/main/README.md)
Chinese Traditional (zh-TW) | [README_zh-TW](https://github.com/Angus-repo/xliff-trans/blob/main/README_zh-TW.md)
Czech (cs)                  | [README_cs](https://github.com/Angus-repo/xliff-trans/blob/main/README_cs.md)

# Xliff-trans Translation Tool

This tool supports the xliff(1.2) format and performs XLIFF translation through external AI services (such as ChatGPT).

### Instructions:

#### 1. Export XLIFF file from Crowdin:
In this example, we use Crowdin. Choose "Export in XLIFF" from your project, then download the file.
![image](https://github.com/user-attachments/assets/fc8517fb-1888-4b60-a694-529269f6c037)

#### 2. Download the program
![xliff-trans release](https://github.com/Angus-repo/xliff-trans/releases/tag/v0.1.1):

```bash
java -jar xliff-trans-x.x.x.jar
```

Select the appropriate file for your operating system:

##### Windows users:
This program is written in Java and requires JDK1.8 or JRE8 or later versions. If JDK8 or above is not installed, please first visit [Java download](https://www.oracle.com/tw/java/technologies/downloads/)
```bash
 xliff-trans-0.1.1.zip
```

##### macOS users:
If you have Java 8 or higher installed, download:

```bash
 xliff-trans-x.x.x-macos.tar.gz
```

If you do not have Java 8 or higher, or are unsure, download the version that includes the Java runtime:

```bash
 xliff-trans-x.x.x-macos-withJRE.tar.gz
```

##### Linux users:
This program is written in Java and requires JDK1.8 or JRE8 or later versions. If JDK8 or above is not installed, please first visit [Java download](https://www.oracle.com/tw/java/technologies/downloads/)
```bash
 xliff-trans-x.x.x.tar.gz
```

After downloading, please extract the files first.

<a id="ai-translation"></a>
#### 3. AI Translation Setup (Optional):

##### &nbsp;&nbsp;&nbsp;&nbsp;3.1 Edit prefix_promp.txt (Optional):
&nbsp;&nbsp;&nbsp;&nbsp;If you plan to use AI-based token translation, write the target language and specify any translation terms or tone in the prefix_promp.txt.
> **Note**: The quality of your translation depends on the AI model and your promp, so make sure to input the desired translation style fully in the prefix_promp.txt.

##### &nbsp;&nbsp;&nbsp;&nbsp;3.2 Specify OpenAI Key in Config.properties (Optional):
&nbsp;&nbsp;&nbsp;&nbsp;Enter your OpenAI key token (additional fees apply). Follow this video for setup:
[Watch the video](https://youtu.be/lrLBq2M-GZk?t=225)

After obtaining the api_key, enter it in Config.properties

```bash
api_key=sk-proj-xxxxxxxxxxxxxxxxxxxxxx
```

If you need to modify the AI model, specify it here

```bash
model_version=gpt-4o-mini
```

> **Note**: The models you can use depend on the [Usage tiers](https://platform.openai.com/docs/guides/rate-limits/usage-tiers). If you've paid $5, you can be a Tier1 user. [See Tier1 details here](https://platform.openai.com/docs/guides/rate-limits/tier-1-rate-limits).


#### 4. Start xliff-trans:
##### Windows users:
Run:

```bash
run.bat
```

Note: Windows may display a security warning. Click "More info" => "Run anyway".

![image](https://github.com/user-attachments/assets/d84d068e-1bec-460d-b6ef-4d92b3d51a50)

![image](https://github.com/user-attachments/assets/0b6e7bf8-c7b8-4d54-bf16-488e2a30097d)

##### macOS users:
Run:

```bash
./run_macos.sh
```

or

```bash
./run_macos_withJRE.sh
```

##### Linux users:
Run:

```bash
java -cp . -jar xliff-trans-0.1.1.jar
```

After starting the program, you will see file path selections, action buttons, and log output at the bottom. First, choose the path of the XLIFF file you want to translate (e.g., /path/xxx.xliff).

#### 5. Step 1 - Extract text for translation:
Select the XLIFF file path and press "Step 1". The program will extract text with the needs-transcate attribute and save it to /path/xxx.xliff_source_text.txt. This will contain only <source id="xxx">source text</source> for AI translation use.

#### 6. Step 2 - Start Translation:
##### &nbsp;&nbsp;&nbsp;&nbsp;Method 1: Use the ChatGPT web interface:
&nbsp;&nbsp;&nbsp;&nbsp;Copy paragraphs (suggested 100 lines) from /path/xxx.xliff_source.txt, paste them into the ChatGPT conversation. Depending on your model, translation speed and accuracy may vary. For example, using GPT-4o, paste 100 lines at a time, wait for the result, and then save the result to /path/xxx.xliff_target.txt.

##### &nbsp;&nbsp;&nbsp;&nbsp;Method 2: Use the OpenAI API ([Additional fees apply](https://openai.com/api/pricing/)):
&nbsp;&nbsp;&nbsp;&nbsp;You need to bind a credit card and generate an API token.
&nbsp;&nbsp;&nbsp;&nbsp;> **Note**: Make sure you've entered the api_key, refer to [3. AI Translation Setup (Optional)](#ai-translation)

&nbsp;&nbsp;&nbsp;&nbsp;In the prefix_promp.txt, specify the target language and mention any specific translation rules for terms. Once everything is ready, press "Step 2" to send 50 lines of text to OpenAI at a time, using the default GPT-4o-mini. The result will be saved in /path/xxx.xliff_target.txt.

Non-source tag data will be skipped in "Step 3", so if incorrect XML structures occur, you can ignore them.
API usage fees can be tracked at https://platform.openai.com/usage (updates may have a few minutes delay).

#### 7. Step 3 - Merge translation into XLIFF:
Click "Step 3" to merge the translation from /path/xxx.xliff_target.txt into the original XLIFF file based on the ID, then save it as /path/xxx.xliff_update.xliff and change the target attribute to translated

```xml
<target state="translated">xxxxxx</target>
```

#### 8. Upload the translated XLIFF:
Upload the /path/xxx.xliff_update.xliff to Crowdin and check the results.
