# Supported Languages

Language                    | Guidelines
----------------------------|----------------
English (en)                | [README](https://github.com/Angus-repo/xliff-trans/blob/main/README.md)
Chinese Traditional (zh-TW) | [README_zh-TW](https://github.com/Angus-repo/xliff-trans/blob/main/README_zh-TW.md)
Czech (cs)                  | [README_cs](https://github.com/Angus-repo/xliff-trans/blob/main/README_cs.md)

# Xliff-trans Nástroj pro překlad

Tento nástroj podporuje formát xliff (1.2) a provádí překlad XLIFF prostřednictvím externích AI služeb (např. ChatGPT).

### Návod:

#### 1. Exportujte soubor XLIFF z Crowdin:
V tomto příkladu používáme Crowdin. Vyberte možnost „Export in XLIFF“ ze svého projektu a poté stáhněte soubor.
![image](https://github.com/user-attachments/assets/fc8517fb-1888-4b60-a694-529269f6c037)

#### 2. Stáhněte program
![xliff-trans release](https://github.com/Angus-repo/xliff-trans/releases/tag/v0.1.1):

```bash
java -jar xliff-trans-x.x.x.jar
```

Vyberte vhodný soubor pro váš operační systém:

##### Uživatelé Windows:
Tento program je napsán v jazyce Java a vyžaduje JDK1.8 nebo JRE8 nebo novější verze. Pokud nemáte nainstalovaný JDK8 nebo novější, navštivte prosím nejprve stažení Java.
```bash
 xliff-trans-0.1.1.zip
```

##### Uživatelé macOS:
Pokud máte nainstalovanou Javu 8 nebo vyšší, stáhněte si:

```bash
 xliff-trans-x.x.x-macos.tar.gz
```

Pokud nemáte Javu 8 nebo vyšší, nebo si nejste jisti, stáhněte si verzi obsahující Java runtime:

```bash
 xliff-trans-x.x.x-macos-withJRE.tar.gz
```

##### Uživatelé Linuxu:
Tento program je napsán v jazyce Java a vyžaduje JDK1.8 nebo JRE8 nebo novější verze. Pokud nemáte nainstalovaný JDK8 nebo novější, navštivte prosím nejprve stažení Java.
```bash
 xliff-trans-x.x.x.tar.gz
```

Po stažení nejprve rozbalte soubory.

<a id="ai-translation"></a>
#### 3. Nastavení AI překladu (Volitelné):

##### &nbsp;&nbsp;&nbsp;&nbsp;3.1 Upravte prefix_promp.txt (Volitelné):
&nbsp;&nbsp;&nbsp;&nbsp;Pokud plánujete použít překlad založený na AI tokenu, napište do souboru prefix_promp.txt cílový jazyk a specifikujte překlad jednotlivých slov, tón atd.
> **Poznámka**: Kvalita vašeho překladu závisí na AI modelu a vašem prompu, proto do souboru prefix_promp.txt zadejte požadovaný styl překladu v plné míře.

##### &nbsp;&nbsp;&nbsp;&nbsp;3.2 Zadejte OpenAI klíč v Config.properties (Volitelné):
&nbsp;&nbsp;&nbsp;&nbsp;Zadejte svůj token OpenAI klíče (další poplatky se vztahují). Postupujte podle tohoto videa pro nastavení:
[Podívejte se na video](https://youtu.be/lrLBq2M-GZk?t=225)

Po získání api_key jej zadejte do Config.properties

```bash
api_key=sk-proj-xxxxxxxxxxxxxxxxxxxxxx
```

Pokud potřebujete změnit AI model, zadejte jej zde

```bash
model_version=gpt-4o-mini
```

> **Poznámka**: Modely, které můžete používat, závisí na [Úrovních použití](https://platform.openai.com/docs/guides/rate-limits/usage-tiers). Pokud jste zaplatili 5 $, můžete být uživatelem Tier1. [Podrobnosti o Tier1 naleznete zde](https://platform.openai.com/docs/guides/rate-limits/tier-1-rate-limits).


#### 4. Spusťte xliff-trans:
##### Uživatelé Windows:
Spusťte:

```bash
run.bat
```

Poznámka: Windows může zobrazit varování o bezpečnosti. Klikněte na „Více informací“ => „Přesto spustit“.

![image](https://github.com/user-attachments/assets/d84d068e-1bec-460d-b6ef-4d92b3d51a50)

![image](https://github.com/user-attachments/assets/0b6e7bf8-c7b8-4d54-bf16-488e2a30097d)

##### Uživatelé macOS:
Spusťte:

```bash
./run_macos.sh
```

nebo

```bash
./run_macos_withJRE.sh
```

##### Uživatelé Linuxu:
Spusťte:

```bash
java -cp . -jar xliff-trans-0.1.1.jar
```

Po spuštění programu uvidíte výběr cest k souborům, tlačítka akcí a výstup logů v dolní části. Nejprve vyberte cestu k souboru XLIFF, který chcete přeložit (např. /path/xxx.xliff).

#### 5. Krok 1 - Extrahujte text pro překlad:
Vyberte cestu k souboru XLIFF a stiskněte „Krok 1“. Program extrahuje text s atributem needs-transcate a uloží jej do /path/xxx.xliff_source_text.txt. Toto bude obsahovat pouze <source id="xxx">source text</source> pro použití při překladu AI.

#### 6. Krok 2 - Spusťte překlad:
##### &nbsp;&nbsp;&nbsp;&nbsp;Metoda 1: Použijte webové rozhraní ChatGPT:
&nbsp;&nbsp;&nbsp;&nbsp;Zkopírujte odstavce (doporučeno 100 řádků) z /path/xxx.xliff_source.txt a vložte je do konverzace ChatGPT. V závislosti na vašem modelu se rychlost a přesnost překladu může lišit. Například při použití GPT-4o vložte 100 řádků najednou, počkejte na výsledek a poté výsledek uložte do /path/xxx.xliff_target.txt.

##### &nbsp;&nbsp;&nbsp;&nbsp;Metoda 2: Použijte OpenAI API ([Další poplatky](https://openai.com/api/pricing/)):
&nbsp;&nbsp;&nbsp;&nbsp;Potřebujete připojit kreditní kartu a vygenerovat token API.
&nbsp;&nbsp;&nbsp;&nbsp;> **Poznámka**: Ujistěte se, že jste zadali api_key, viz [3. Nastavení AI překladu (Volitelné)](#ai-translation)

&nbsp;&nbsp;&nbsp;&nbsp;V souboru prefix_promp.txt specifikujte cílový jazyk a zmiňte jakékoli speciální pravidla pro překlad termínů. Jakmile je vše připraveno, stiskněte „Krok 2“, aby se 50 řádků textu najednou odeslalo do OpenAI, s výchozím GPT-4o-mini. Výsledek bude uložen do /path/xxx.xliff_target.txt.

Data mimo tag source budou přeskočena v „Kroku 3“, takže pokud dojde k nesprávné struktuře XML, můžete je ignorovat.
Poplatky za použití API můžete sledovat na https://platform.openai.com/usage (aktualizace může mít několik minut zpoždění).

#### 7. Krok 3 - Sloučte překlad do XLIFF:
Klikněte na „Krok 3“ a sloučte překlad z /path/xxx.xliff_target.txt do původního souboru XLIFF na základě ID, poté jej uložte jako /path/xxx.xliff_update.xliff a změňte atribut target na přeloženo

```xml
<target state="translated">xxxxxx</target>
```

#### 8. Nahrajte přeložený XLIFF:
Nahrajte /path/xxx.xliff_update.xliff do Crowdin a zkontrolujte výsledky.
