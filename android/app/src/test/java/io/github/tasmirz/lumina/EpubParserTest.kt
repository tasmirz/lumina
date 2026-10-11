package io.github.tasmirz.lumina

import io.github.tasmirz.lumina.data.EpubParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubParserTest {

    @Test
    fun testParseEpubExtractsChaptersAndCleansHtml() {
        val baos = ByteArrayOutputStream()
        val zos = ZipOutputStream(baos)

        // Mock an XHTML chapter entry
        val entry = ZipEntry("OEBPS/chapter1.xhtml")
        zos.putNextEntry(entry)
        val htmlContent = """
            <?xml version="1.0" encoding="utf-8"?>
            <!DOCTYPE html>
            <html xmlns="http://www.w3.org/1999/xhtml">
            <head><title>Chapter 1</title></head>
            <body>
              <h1>Chapter I</h1>
              <p>One morning, when Gregor Samsa woke from troubled dreams, he found himself transformed in his bed into a horrible vermin.</p>
              <p>The bedding was hardly able to cover it and seemed ready to slide off any moment. His many legs, pitifully thin compared with the size of the rest of him, waved about helplessly as he looked.</p>
            </body>
            </html>
        """.trimIndent()
        zos.write(htmlContent.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
        zos.close()

        val bais = ByteArrayInputStream(baos.toByteArray())
        val book = EpubParser.parseEpub(bais, "The_Metamorphosis.epub")

        assertEquals("The Metamorphosis", book.title)
        assertTrue(book.chapters.isNotEmpty())
        val firstChapter = book.chapters.first()
        assertTrue(firstChapter.paragraphs.isNotEmpty())
        assertTrue(firstChapter.paragraphs.any { it.contains("Gregor Samsa") })
    }

    @Test
    fun testIsHeadingOnlyDetection() {
        assertTrue(EpubParser.isHeadingOnly("Chapter 1", "Chapter 1", "Nineteen Eighty-Four", "Part One"))
        assertTrue(EpubParser.isHeadingOnly("Chapter 1Chapter 1", "Chapter 1", "Nineteen Eighty-Four", "Part One"))
        assertTrue(EpubParser.isHeadingOnly("Part One", "Chapter 1", "Nineteen Eighty-Four", "Part One"))
        assertTrue(EpubParser.isHeadingOnly("Nineteen Eighty-Four", "Chapter 1", "Nineteen Eighty-Four", "Part One"))
        assertTrue(EpubParser.isHeadingOnly("Chapter 2", "Chapter 2", "Book Title", ""))
        assertTrue(EpubParser.isHeadingOnly("Chapter IV", "Chapter IV", "Book Title", ""))
        org.junit.Assert.assertFalse(EpubParser.isHeadingOnly(
            "It was a bright cold day in April, and the clocks were striking thirteen.",
            "Chapter 1",
            "Nineteen Eighty-Four",
            "Part One"
        ))
    }

    @Test
    fun testParseEpubLanguageExtraction() {
        val baos = ByteArrayOutputStream()
        val zos = ZipOutputStream(baos)

        // Add container.xml
        zos.putNextEntry(ZipEntry("META-INF/container.xml"))
        val containerXml = """
            <?xml version="1.0"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles>
                <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
              </rootfiles>
            </container>
        """.trimIndent()
        zos.write(containerXml.toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        // Add content.opf with dc:language
        zos.putNextEntry(ZipEntry("OEBPS/content.opf"))
        val opfXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>Le Petit Prince</dc:title>
                <dc:creator>Antoine de Saint-Exupéry</dc:creator>
                <dc:language>fr-FR</dc:language>
              </metadata>
              <manifest>
                <item id="c1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
              </manifest>
              <spine>
                <itemref idref="c1"/>
              </spine>
            </package>
        """.trimIndent()
        zos.write(opfXml.toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        // Add chapter1.xhtml
        zos.putNextEntry(ZipEntry("OEBPS/chapter1.xhtml"))
        val htmlContent = """
            <?xml version="1.0" encoding="utf-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml">
            <body><p>Bonjour le monde.</p></body>
            </html>
        """.trimIndent()
        zos.write(htmlContent.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
        zos.close()

        val book = EpubParser.parseEpub(ByteArrayInputStream(baos.toByteArray()), "test.epub")
        assertEquals("fr", book.language)
        assertEquals("Le Petit Prince", book.title)
    }

    @Test
    fun testParseFrankenstein() {
        val file = java.io.File("/tmp/epub_test/frankenstein.epub")
        if (!file.exists()) return
        val book = EpubParser.parseEpub(file.inputStream(), file.name)
        println("Frankenstein chapters count: ${book.chapters.size}")
        println("Frankenstein title: ${book.title}")
        assertTrue("Chapters should not be empty", book.chapters.isNotEmpty())
    }

    @Test
    fun testParsePride() {
        val file = java.io.File("/tmp/epub_test/pride.epub")
        if (!file.exists()) return
        val book = EpubParser.parseEpub(file.inputStream(), file.name)
        println("Pride chapters count: ${book.chapters.size}")
        println("Pride title: ${book.title}")
        assertTrue("Chapters should not be empty", book.chapters.isNotEmpty())
    }

    @Test
    fun testParseEpubFilenameResolution() {
        val baos = ByteArrayOutputStream()
        val zos = ZipOutputStream(baos)
        zos.putNextEntry(ZipEntry("OEBPS/ch1.xhtml"))
        zos.write("<html><body><p>Test paragraph content.</p></body></html>".toByteArray(Charsets.UTF_8))
        zos.closeEntry()
        zos.close()

        val book1 = EpubParser.parseEpub(ByteArrayInputStream(baos.toByteArray()), "My_Favorite_Book.epub")
        assertEquals("My Favorite Book", book1.title)

        val book2 = EpubParser.parseEpub(ByteArrayInputStream(baos.toByteArray()), "1694380000_sample_story.epub")
        assertEquals("Sample Story", book2.title)
    }

    @Test
    fun testParseBookMetadataFast() {
        val tempFile = java.io.File.createTempFile("lumina_meta_test", ".epub")
        try {
            val zos = ZipOutputStream(tempFile.outputStream())
            zos.putNextEntry(ZipEntry("META-INF/container.xml"))
            zos.write("""<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("content.opf"))
            val opfContent = """
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                    <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Fast Metadata Book</dc:title>
                        <dc:creator>Test Author</dc:creator>
                        <dc:language>en</dc:language>
                    </metadata>
                    <manifest>
                        <item id="c1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
                    </manifest>
                </package>
            """.trimIndent()
            zos.write(opfContent.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
            zos.close()

            val book = EpubParser.parseBookMetadata(tempFile)
            assertEquals("Fast Metadata Book", book.title)
            assertEquals("Test Author", book.author)
            assertEquals("en", book.language)
            assertEquals(tempFile.absolutePath, book.filePath)
            assertTrue(book.chapters.isEmpty())
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testDecodeHtmlNumericAndHexEntities() {
        val input = "&#x1D40F;&#x00028;x&#x0002C;y&#x00029; = &#x1D415; &plusmn; &#8730;&#x00028;a&#x0002B;b&#x00029;"
        val decoded = EpubParser.decodeHtmlEntities(input)
        assertEquals("𝐏(x,y) = 𝐕 ± √(a+b)", decoded)

        // Mathematical bold characters
        assertEquals("𝐏", EpubParser.decodeHtmlEntities("&#x1D40F;"))
        assertEquals("𝐕", EpubParser.decodeHtmlEntities("&#x1D415;"))
        assertEquals("𝐌", EpubParser.decodeHtmlEntities("&#x1D40C;"))
        assertEquals("𝐍", EpubParser.decodeHtmlEntities("&#x1D40D;"))
        assertEquals("𝐢", EpubParser.decodeHtmlEntities("&#x1D422;"))
        assertEquals("θ", EpubParser.decodeHtmlEntities("&#x003B8;"))
        assertEquals("—", EpubParser.decodeHtmlEntities("&#8212;"))
        assertEquals("–", EpubParser.decodeHtmlEntities("&#8211;"))
        assertEquals("‘test’", EpubParser.decodeHtmlEntities("&#8216;test&#8217;"))
        assertEquals("“quoted”", EpubParser.decodeHtmlEntities("&#8220;quoted&#8221;"))
    }

    @Test
    fun testTransformMathMLBasicAndComplex() {
        // Basic vector formula
        val math1 = """<math xmlns="http://www.w3.org/1998/Math/MathML"><mrow><mi>&#x1D415;</mi><mo>&#x0003D;</mo><mo stretchy="false">[</mo><mi>a</mi><mo>&#x0002C;</mo><mi>b</mi><mo>&#x0002C;</mo><mi>c</mi><mo stretchy="false">]</mo></mrow></math>"""
        assertEquals("𝐕 = [a, b, c]", EpubParser.transformMathML(math1))

        // Norm with sqrt and superscript
        val math2 = """<math xmlns="http://www.w3.org/1998/Math/MathML"><mrow><mo stretchy="false">&#x0007C;</mo><mi>&#x1D415;</mi><mo stretchy="false">&#x0007C;</mo><mo>&#x0003D;</mo><msqrt><mrow><msup><mi>a</mi><mn>2</mn></msup><mo>&#x0002B;</mo><msup><mi>b</mi><mn>2</mn></msup><mo>&#x0002B;</mo><msup><mi>c</mi><mn>2</mn></msup></mrow></msqrt></mrow></math>"""
        assertEquals("|𝐕| = √(a² + b² + c²)", EpubParser.transformMathML(math2))

        // Subscripts and trigonometric functions
        val math3 = """<math xmlns="http://www.w3.org/1998/Math/MathML"><mrow><msub><mi>&#x1D415;</mi><mn>1</mn></msub><mo>&#x000B7;</mo><msub><mi>&#x1D415;</mi><mn>2</mn></msub><mo>&#x0003D;</mo><msub><mi>x</mi><mn>1</mn></msub><msub><mi>x</mi><mn>2</mn></msub><mo>&#x0002B;</mo><msub><mi>y</mi><mn>1</mn></msub><msub><mi>y</mi><mn>2</mn></msub><mo>&#x0003D;</mo><mo stretchy="false">&#x0007C;</mo><msub><mi>&#x1D415;</mi><mn>1</mn></msub><mo stretchy="false">&#x0007C;</mo><mo stretchy="false">&#x0007C;</mo><msub><mi>&#x1D415;</mi><mn>2</mn></msub><mo stretchy="false">&#x0007C;</mo><mi>cos</mi><mi>&#x003B8;</mi></mrow></math>"""
        val res3 = EpubParser.transformMathML(math3)
        assertTrue(res3.contains("𝐕₁ · 𝐕₂ = x₁x₂ + y₁y₂"))
        assertTrue(res3.contains("cos θ"))

        // Subscript f with parameters
        val math4 = """<math xmlns="http://www.w3.org/1998/Math/MathML" display="inline"><mrow><msub><mi>&#x1D40F;</mi><mi>f</mi></msub><mo stretchy="false">&#x00028;</mo><msub><mi>x</mi><mi>f</mi></msub><mo>&#x0002C;</mo><msub><mi>y</mi><mi>f</mi></msub><mo>&#x0002C;</mo><msub><mi>z</mi><mi>f</mi></msub><mo stretchy="false">&#x00029;</mo></mrow></math>"""
        assertEquals("𝐏[sub]f[/sub](x[sub]f[/sub], y[sub]f[/sub], z[sub]f[/sub])", EpubParser.transformMathML(math4))

        // Fraction
        val math5 = """<math xmlns="http://www.w3.org/1998/Math/MathML"><mfrac><mrow><mi>&#x003B8;</mi></mrow><mrow><mn>2</mn></mrow></mfrac></math>"""
        assertEquals("θ/2", EpubParser.transformMathML(math5))

        val math6 = """<math xmlns="http://www.w3.org/1998/Math/MathML"><mtable><mtr><mtd><mi>&#x1D422;</mi></mtd><mtd><mi>&#x1D423;</mi></mtd></mtr><mtr><mtd><msub><mi>x</mi><mn>1</mn></msub></mtd><mtd><msub><mi>y</mi><mn>1</mn></msub></mtd></mtr></mtable></math>"""
        val res6 = EpubParser.transformMathML(math6)
        assertTrue(res6.contains("𝐢") && res6.contains("𝐣"))
        assertTrue(res6.contains("x₁") && res6.contains("y₁"))
    }

    @Test
    fun testParseEpubInlineImagesAndCaptions() {
        val baos = ByteArrayOutputStream()
        val zos = ZipOutputStream(baos)

        // Structure files
        zos.putNextEntry(ZipEntry("META-INF/container.xml"))
        zos.write("""<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""".toByteArray())
        zos.closeEntry()

        zos.putNextEntry(ZipEntry("OEBPS/content.opf"))
        val opfXml = """
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>Math and Images Book</dc:title>
                <dc:creator>Graphics Author</dc:creator>
                <dc:language>en</dc:language>
              </metadata>
              <manifest>
                <item id="c1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
                <item id="fig1" href="figures/fig_test.png" media-type="image/png"/>
              </manifest>
              <spine>
                <itemref idref="c1"/>
              </spine>
            </package>
        """.trimIndent()
        zos.write(opfXml.toByteArray())
        zos.closeEntry()

        // Dummy PNG image
        zos.putNextEntry(ZipEntry("OEBPS/figures/fig_test.png"))
        zos.write("fake_png_binary_stream_bytes".toByteArray())
        zos.closeEntry()

        // Chapter with figure, img, caption, and MathML
        zos.putNextEntry(ZipEntry("OEBPS/ch1.xhtml"))
        val ch1Html = """
            <?xml version="1.0" encoding="utf-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml">
            <head><title>Chapter 1: 3D Vectors</title></head>
            <body>
              <h1>Chapter 1: 3D Vectors</h1>
              <p>Consider the vector <span class="math inline"><math xmlns="http://www.w3.org/1998/Math/MathML" display="inline"><mrow><mi>&#x1D415;</mi><mo>&#x0003D;</mo><mo stretchy="false">[</mo><mi>a</mi><mo>&#x0002C;</mo><mi>b</mi><mo>&#x0002C;</mo><mi>c</mi><mo stretchy="false">]</mo></mrow></math></span> in 3D space.</p>
              <figure>
                <img src="figures/fig_test.png" alt="Vector Diagram"/>
                <figcaption>Illustration of 3D Vector</figcaption>
              </figure>
              <div class="math display">
                <math xmlns="http://www.w3.org/1998/Math/MathML" display="block">
                  <mrow><mo stretchy="false">&#x0007C;</mo><mi>&#x1D415;</mi><mo stretchy="false">&#x0007C;</mo><mo>&#x0003D;</mo><msqrt><mrow><msup><mi>a</mi><mn>2</mn></msup><mo>&#x0002B;</mo><msup><mi>b</mi><mn>2</mn></msup><mo>&#x0002B;</mo><msup><mi>c</mi><mn>2</mn></msup></mrow></msqrt></mrow>
                </math>
              </div>
            </body>
            </html>
        """.trimIndent()
        zos.write(ch1Html.toByteArray())
        zos.closeEntry()
        zos.close()

        val book = EpubParser.parseEpub(ByteArrayInputStream(baos.toByteArray()), "test_math_images.epub")
        assertEquals("Math and Images Book", book.title)
        assertTrue(book.chapters.isNotEmpty())
        val paras = book.chapters.first().paragraphs

        // 1. MathML inline should be cleanly formatted, no raw entities
        assertTrue(paras.any { it.contains("Consider the vector 𝐕 = [a, b, c] in 3D space.") })
        assertTrue(paras.none { it.contains("&#x1D415;") || it.contains("&#x0002C;") })

        // 2. Inline image token should be present and point to an existing extracted file
        val imgPara = paras.firstOrNull { it.startsWith("[IMG:") && it.endsWith("]") }
        org.junit.Assert.assertNotNull("Image token paragraph must be extracted", imgPara)
        val extractedPath = imgPara!!.removePrefix("[IMG:").removeSuffix("]")
        val extractedFile = java.io.File(extractedPath)
        assertTrue("Extracted image file must exist on disk", extractedFile.exists())
        assertEquals("fake_png_binary_stream_bytes", extractedFile.readText())

        // 3. Caption should follow image
        assertTrue(paras.any { it == "Illustration of 3D Vector" })

        // 4. Display Math should be centered on its own paragraph
        assertTrue(paras.any { it == "|𝐕| = √(a² + b² + c²)" })
    }

    @Test
    fun testParse3DTransformationsEpubIfAvailable() {
        val file = java.io.File("/home/user/Sync/4-1/Computer Graphics/4. 3D Transformations.epub")
        if (!file.exists()) return

        val book = EpubParser.parseEpub(file)
        println("3D Transformations book title: ${book.title}")
        println("Chapters count: ${book.chapters.size}")
        assertTrue(book.chapters.isNotEmpty())

        var totalImagesFound = 0

        book.chapters.forEach { chapter ->
            chapter.paragraphs.forEach { para ->
                if (para.startsWith("[IMG:") && para.endsWith("]")) {
                    totalImagesFound++
                    val path = para.removePrefix("[IMG:").removeSuffix("]")
                    val f = java.io.File(path)
                    assertTrue("Image file $path must exist", f.exists())
                    assertTrue("Image file $path must not be empty", f.length() > 0)
                }
                // Assert no raw math entity leak
                org.junit.Assert.assertFalse("Raw &#x1D40F; leaked: $para", para.contains("&#x1D40F;"))
                org.junit.Assert.assertFalse("Raw &#x00028; leaked: $para", para.contains("&#x00028;"))
                org.junit.Assert.assertFalse("Raw &#x0002C; leaked: $para", para.contains("&#x0002C;"))
                org.junit.Assert.assertFalse("Raw &#x1D415; leaked: $para", para.contains("&#x1D415;"))
                org.junit.Assert.assertFalse("Raw stripped mathml leaked: $para", para.contains("<math") || para.contains("</math>"))
            }
        }

        println("Total inline images successfully verified on disk: $totalImagesFound")
        // Verify all 139 inline image references are resolved to valid files
        assertEquals(139, totalImagesFound)

        // Verify all 148 images in the ZIP were saved to disk without exception
        val imagesDir = java.io.File(System.getProperty("java.io.tmpdir", "/tmp"), "lumina_books/${book.id}/images")
        val diskFiles = imagesDir.listFiles()?.count { it.isFile && it.length() > 0 } ?: 0
        assertEquals(148, diskFiles)
    }

    @Test
    fun testParseDeclickbaitAcademicReportIfAvailable() {
        val file = java.io.File("/home/user/Projects/declickbait/report/declickbait_academic_report.epub")
        if (!file.exists()) return

        val book = EpubParser.parseEpub(file)
        assertTrue(book.title.contains("Declickbait"))
        assertEquals(10, book.chapters.size)

        // Chapter 1: Title and Table
        val chap1 = book.chapters[0]
        val chap1Tables = chap1.paragraphs.filter { it.startsWith("[TABLE:") }
        assertEquals(1, chap1Tables.size)
        assertTrue(chap1Tables[0].contains("Submitted By") && chap1Tables[0].contains("Course Teacher"))
        assertTrue(chap1Tables[0].contains("Jahid Hasan") && chap1Tables[0].contains("Tasmir Hossain Zihad"))

        // Chapter 2: Executive Summary & Master Architectural Matrix (2 Tables)
        val chap2 = book.chapters[1]
        val chap2Tables = chap2.paragraphs.filter { it.startsWith("[TABLE:") }
        assertEquals(2, chap2Tables.size)
        assertTrue(chap2Tables[0].contains("Codename") && chap2Tables[0].contains("Primary Operational Role"))
        assertTrue(chap2Tables[0].contains("cls_2t_bpe") && chap2Tables[0].contains("3.59M"))
        assertTrue(chap2Tables[1].contains("System Codename") && chap2Tables[1].contains("Operational Rationale"))

        // Chapter 3: 1. Introduction and Theoretical Problem Formulation
        val chap3 = book.chapters[2]
        assertEquals("1. Introduction and Theoretical Problem Formulation", chap3.title)

        // Lists should have numbered items: 1. Standard Bengali, 2. Romanized Bengali, 3. Standard English
        assertTrue(chap3.paragraphs.any { it.startsWith("1. ") && it.contains("Standard Bengali") })
        assertTrue(chap3.paragraphs.any { it.startsWith("2. ") && it.contains("Romanized Bengali") })
        assertTrue(chap3.paragraphs.any { it.startsWith("3. ") && it.contains("Standard English") })

        // Math should have hat Ŷ and no raw LaTeX backslashes or leaked braces
        assertTrue(chap3.paragraphs.any { it.contains("Ŷ =") || it.contains("Ŷ") })
        assertTrue(chap3.paragraphs.none { it.contains("\\arg") })
        assertTrue(chap3.paragraphs.none { it.contains("v_{OS}") || it.contains("v_{WS}") })

        // Figures verified
        var totalImages = 0
        book.chapters.forEach { c ->
            totalImages += c.paragraphs.count { it.startsWith("[IMG:") }
        }
        assertEquals(8, totalImages)
    }

    @Test
    fun testParseNlpLab3EpubIfAvailable() {
        val file = java.io.File("/home/user/Downloads/processed/NLP Lab 3_ Word Embeddings & Foundational Classifiers.docx.epub")
        if (!file.exists()) return

        val book = EpubParser.parseEpub(file)
        println("NLP Lab 3 title: ${book.title}")
        println("Chapters: ${book.chapters.size}")
        assertTrue(book.chapters.isNotEmpty())

        var inlineImagesCount = 0
        var blockImagesCount = 0
        book.chapters.forEach { c ->
            c.paragraphs.forEach { p ->
                if (p.startsWith("[IMG:") && p.endsWith("]")) {
                    blockImagesCount++
                    val path = p.removePrefix("[IMG:").removeSuffix("]")
                    val f = java.io.File(path)
                    assertTrue("Block image must exist: $path", f.exists())
                }
                if (p.contains("[INLINE_IMG:")) {
                    val count = "\\[INLINE_IMG:([^\\]]+)\\]".toRegex().findAll(p).count()
                    inlineImagesCount += count
                    "\\[INLINE_IMG:([^\\]]+)\\]".toRegex().findAll(p).forEach { m ->
                        val path = m.groupValues[1]
                        val f = java.io.File(path)
                        assertTrue("Inline image must exist: $path", f.exists())
                    }
                }
            }
        }
        println("Block images: $blockImagesCount, Inline images: $inlineImagesCount")
        assertTrue("Must extract inline images", inlineImagesCount > 0)

        var inlineImagesParsed = 0
        var mathContextHits = 0
        book.chapters.forEach { c ->
            c.paragraphs.forEach { p ->
                if (p.contains("[INLINE_IMG:")) {
                    val parsed = io.github.tasmirz.lumina.ui.reader.parseTextFormatting(p)
                    inlineImagesParsed += parsed.inlineImages.size
                    if (p.contains("[sub]") || p.contains("λ") || p.contains("α") || p.contains("∑")) {
                        mathContextHits++
                        println("Math context hit with inline img: $p")
                    }
                    org.junit.Assert.assertFalse("Must not leak raw [INLINE in plain text: ${parsed.plainText}", parsed.plainText.contains("[INLINE"))
                }
            }
        }
        println("Inline images parsed: $inlineImagesParsed, Math context hits: $mathContextHits")
        assertEquals("All extracted inline images must be parsed by parseTextFormatting", inlineImagesCount, inlineImagesParsed)

        // Test the exact paragraph from user's screenshot
        val problematicPara = "Because vector addition is commutative ( [INLINE_IMG:/data/user/0/io.github.tasmirz.lumina/files/books/custom-1791678701214/images/custom-1791678701214_GoogleDocimagesimage39.png] ): ∑ Weight(w) · v_w"
        val parsed = io.github.tasmirz.lumina.ui.reader.parseTextFormatting(problematicPara)
        println("Problematic para parsed plainText: ${parsed.plainText}")
        println("Problematic para inlineImages count: ${parsed.inlineImages.size}")
        assertEquals(1, parsed.inlineImages.size)
        org.junit.Assert.assertFalse(parsed.plainText.contains("[INLINE"))
    }

    @Test
    fun testMathMlUnderscoreSubscriptStrictIsolation() {
        // 1. MathML with underscore subscript must convert cleanly
        val mathWithSub = "<math><mi>v_cat</mi></math>"
        val transformed = EpubParser.transformMathML(mathWithSub)
        assertEquals("v[sub]cat[/sub]", transformed)

        val mathWithDigitSub = "<math><mi>x_1</mi></math>"
        val transformedDigit = EpubParser.transformMathML(mathWithDigitSub)
        assertEquals("x₁", transformedDigit)

        // 2. Prose text with underscores must NEVER be altered into subscripts
        val proseWithUnderscore = "NLP Lab 3_ Word Embeddings & Foundational Classifiers"
        val parsedProse = io.github.tasmirz.lumina.ui.reader.parseTextFormatting(proseWithUnderscore)
        assertEquals("NLP Lab 3_ Word Embeddings & Foundational Classifiers", parsedProse.plainText)
        assertTrue("No spans should be added to prose underscores", parsedProse.spans.isEmpty())
        assertTrue("No inline images", parsedProse.inlineImages.isEmpty())
    }

    @Test
    fun testInlineMathSpacingAndPunctuationBinding() {
        // 1. Word collision prevention: no space before tag
        val unspaced = "continuous vector space[INLINE_IMG:math_r_d.png] (where"
        val parsedUnspaced = io.github.tasmirz.lumina.ui.reader.parseTextFormatting(unspaced)
        assertEquals("continuous vector space \uFFFC (where", parsedUnspaced.plainText)

        // 2. Multi-space collapse before inline tag
        val multiSpaced = "with a single  [INLINE_IMG:math_1.png]  and"
        val parsedMultiSpaced = io.github.tasmirz.lumina.ui.reader.parseTextFormatting(multiSpaced)
        assertEquals("with a single \uFFFC and", parsedMultiSpaced.plainText)

        // 3. Punctuation binding: comma, colon, period, closing parenthesis
        val commaSpaced = "vocabulary size  [INLINE_IMG:math_v.png]  , every word"
        val parsedComma = io.github.tasmirz.lumina.ui.reader.parseTextFormatting(commaSpaced)
        assertEquals("vocabulary size \uFFFC, every word", parsedComma.plainText)

        val colonSpaced = "sliding window  [INLINE_IMG:math_c.png]  :"
        val parsedColon = io.github.tasmirz.lumina.ui.reader.parseTextFormatting(colonSpaced)
        assertEquals("sliding window \uFFFC:", parsedColon.plainText)

        val parenSpaced = "Because vector addition is commutative ( [INLINE_IMG:math_eq.png] ):"
        val parsedParen = io.github.tasmirz.lumina.ui.reader.parseTextFormatting(parenSpaced)
        assertEquals("Because vector addition is commutative (\uFFFC):", parsedParen.plainText)
    }

    @Test
    fun testIsSceneDividerGlyph() {
        assertTrue(EpubParser.isSceneDividerGlyph("⁂"))
        assertTrue(EpubParser.isSceneDividerGlyph("* * *"))
        assertTrue(EpubParser.isSceneDividerGlyph("***"))
        assertTrue(EpubParser.isSceneDividerGlyph("---"))
        assertTrue(EpubParser.isSceneDividerGlyph("• • •"))
        org.junit.Assert.assertFalse(EpubParser.isSceneDividerGlyph("Normal paragraph text with an * in it"))
    }

    @Test
    fun testSanitizeDisplayTitle() {
        assertEquals("Chapter 3: Word Embeddings", io.github.tasmirz.lumina.ui.reader.sanitizeDisplayTitle("Chapter 3_ Word Embeddings.epub"))
        assertEquals("Nineteen Eighty-Four", io.github.tasmirz.lumina.ui.reader.sanitizeDisplayTitle("Nineteen Eighty-Four.EPUB"))
        assertEquals("NLP Lab 3: Word Embeddings", io.github.tasmirz.lumina.ui.reader.sanitizeDisplayTitle("NLP Lab 3_ Word Embeddings"))
    }
}


