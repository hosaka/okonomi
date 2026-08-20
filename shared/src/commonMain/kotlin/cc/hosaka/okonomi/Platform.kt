package cc.hosaka.okonomi

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform