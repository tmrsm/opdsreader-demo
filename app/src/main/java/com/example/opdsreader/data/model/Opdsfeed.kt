package com.example.opdsreader.data.model

import org.simpleframework.xml.Element
import org.simpleframework.xml.ElementList
import org.simpleframework.xml.Root

@Root(name = "feed", strict = false)
data class OpdsFeed(
    @field:Element(name = "title", required = false)
    var title: String? = null,

    @field:ElementList(name = "entry", inline = true, required = false)
    var entries: List<OpdsEntry>? = null
)