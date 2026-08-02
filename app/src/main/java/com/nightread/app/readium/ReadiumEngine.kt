package com.nightread.app.readium

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.services.search.search
import org.readium.r2.shared.publication.services.search.SearchIterator
import org.readium.r2.shared.publication.services.search.SearchService
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.getOrElse
import org.readium.adapter.pdfium.document.PdfiumDocumentFactory
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import org.readium.r2.navigator.epub.EpubNavigatorFactory

class ReadiumEngine private constructor(private val context: Context) {

    private val httpClient = DefaultHttpClient()
    private val assetRetriever = AssetRetriever(context.contentResolver, httpClient)
    private val pdfiumFactory = PdfiumDocumentFactory(context)
    private val publicationOpener = PublicationOpener(
        publicationParser = DefaultPublicationParser(context, httpClient, assetRetriever, pdfFactory = pdfiumFactory)
    )

    suspend fun openPublication(file: File): Publication? = withContext(Dispatchers.IO) {
        try {
            val asset = assetRetriever.retrieve(file).getOrElse { return@withContext null }
            publicationOpener.open(asset, allowUserInteraction = false).getOrElse { null }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun createNavigatorFactory(publication: Publication): EpubNavigatorFactory {
        return EpubNavigatorFactory(publication)
    }

    suspend fun searchPublication(publication: Publication, query: String): SearchIterator? = withContext(Dispatchers.IO) {
        try {
            publication.search(query)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: ReadiumEngine? = null

        @Volatile
        var currentPublication: Publication? = null

        fun getInstance(context: Context): ReadiumEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ReadiumEngine(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun getPublication(): Publication? = currentPublication

        suspend fun openPublication(context: Context, file: File): Publication? {
            val processedFile = Fb2ToEpubConverter.convertIfNeeded(context, file)
            val pub = getInstance(context).openPublication(processedFile)
            currentPublication = pub
            return pub
        }
    }
}
