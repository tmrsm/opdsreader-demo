package com.example.opdsreader.data.model

import org.simpleframework.xml.Attribute
import org.simpleframework.xml.Element
import org.simpleframework.xml.ElementList
import org.simpleframework.xml.Namespace
import org.simpleframework.xml.Root

@Root(name = "entry", strict = false)
data class OpdsEntry(
    @field:Element(name = "title", required = false)
    var title: String? = null,

    @field:Element(name = "content", required = false)
    var content: String? = null,

    @field:ElementList(name = "link", inline = true, required = false)
    var links: List<OpdsLink>? = null
)

@Root(name = "link", strict = false)
data class OpdsLink(
    @field:Attribute(name = "href", required = false)
    var href: String? = null,

    @field:Attribute(name = "type", required = false)
    var type: String? = null,

    @field:Attribute(name = "rel", required = false)
    var rel: String? = null,

    @field:Attribute(name = "count", required = false)
    var count: String? = null,

    @field:Attribute(name = "lastRead", required = false)
    @field:Namespace(prefix = "p5", reference = "http://vaemendis.net/opds-pse/ns")
    var lastRead: String? = null
)