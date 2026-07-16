package subprojects

import subprojects.build.*

/** Environment variables available on agents. */
object Env {
    /** Path to LTS JDK. */
    val JDK_LTS = "%env.${JDKEntry.JavaLTS.env}%"

    /** Path to JDK 21. */
    val JDK_21 = "%env.${JDKEntry.Java21.env}%"
}
