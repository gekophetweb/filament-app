package nl.jaap.filamentspools.testutil

fun fixture(name: String): String =
    requireNotNull(Thread.currentThread().contextClassLoader?.getResource("fixtures/$name")) {
        "Missing fixture $name"
    }.readText()
