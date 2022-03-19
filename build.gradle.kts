/*
 * Copyright 2020 dorkbox, llc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.time.Instant

///////////////////////////////
//////    PUBLISH TO SONATYPE / MAVEN CENTRAL
////// TESTING : (to local maven repo) <'publish and release' - 'publishToMavenLocal'>
////// RELEASE : (to sonatype/maven central), <'publish and release' - 'publishToSonatypeAndRelease'>
///////////////////////////////

gradle.startParameter.showStacktrace = ShowStacktrace.ALWAYS   // always show the stacktrace!


plugins {
    id("com.dorkbox.GradleUtils") version "2.16"
    id("com.dorkbox.Licensing") version "2.12"
    id("com.dorkbox.VersionUpdate") version "2.4"
    id("com.dorkbox.GradlePublish") version "1.12"

    kotlin("jvm") version "1.6.10"
}

object Extras {
    // set for the project
    const val description = "Niche collections to augment what is already available."
    const val group = "com.dorkbox"
    const val version = "1.0"

    // set as project.ext
    const val name = "Collections"
    const val id = "Collections" // this is the maven ID!
    const val vendor = "Dorkbox LLC"
    const val vendorUrl = "https://dorkbox.com"
    const val url = "https://git.dorkbox.com/dorkbox/Collections"

    val buildDate = Instant.now().toString()
}

///////////////////////////////
/////  assign 'Extras'
///////////////////////////////
GradleUtils.load("$projectDir/../../gradle.properties", Extras)
GradleUtils.defaults()
GradleUtils.compileConfiguration(JavaVersion.VERSION_1_8)
GradleUtils.jpms(JavaVersion.VERSION_1_9)


licensing {
    license(License.APACHE_2) {
        description(Extras.description)
        author(Extras.vendor)
        url(Extras.url)

        extra("AhoCorasickDoubleArrayTrie", License.APACHE_2) {
            description(Extras.description)
            copyright(2018)
            author("hankcs <me@hankcs.com>")
            url("https://github.com/hankcs/AhoCorasickDoubleArrayTrie")
        }
        extra("Bias, BinarySearch", License.MIT) {
            url(Extras.url)
            url("https://github.com/timboudreau/util")
            copyright(2013)
            author("Tim Boudreau")
        }
        extra("ConcurrentEntry", License.APACHE_2) {
            url(Extras.url)
            copyright(2016)
            author("bennidi")
            author("dorkbox")
        }
        extra("Collection Utilities (Array, ArrayMap, BooleanArray, ByteArray, CharArray, FloatArray, IdentityMap, IntArray, IntFloatMap, IntIntMap, IntMap, IntSet, LongArray, LongMap, ObjectFloatMap, ObjectIntMap, ObjectMap, ObjectSet, OrderedMap, OrderedSet)", License.APACHE_2) {
            url(Extras.url)
            url("https://github.com/libgdx/libgdx/blob/master/gdx/src/com/badlogic/gdx/utils")
            copyright(2011)
            author("LibGDX")
            author("Mario Zechner (badlogicgames@gmail.com)")
            author("Nathan Sweet (nathan.sweet@gmail.com)")
        }
        extra("Predicate", License.APACHE_2) {
            url(Extras.url)
            url("https://github.com/libgdx/libgdx/blob/master/gdx/src/com/badlogic/gdx/utils")
            copyright(2011)
            author("LibGDX")
            author("Mario Zechner (badlogicgames@gmail.com)")
            author("Nathan Sweet (nathan.sweet@gmail.com)")
            author("xoppa")
        }
        extra("Select, QuickSelect", License.APACHE_2) {
            url(Extras.url)
            url("https://github.com/libgdx/libgdx/blob/master/gdx/src/com/badlogic/gdx/utils")
            copyright(2011)
            author("LibGDX")
            author("Mario Zechner (badlogicgames@gmail.com)")
            author("Nathan Sweet (nathan.sweet@gmail.com)")
            author("Jon Renner")
        }
        extra("TimSort, ComparableTimSort", License.APACHE_2) {
            url(Extras.url)
            url("https://github.com/libgdx/libgdx/blob/master/gdx/src/com/badlogic/gdx/utils")
            copyright(2008)
            author("The Android Open Source Project")
        }
        extra("ConcurrentWeakIdentityHashMap", License.APACHE_2) {
            copyright(2016)
            description("Concurrent WeakIdentity HashMap")
            author("zhanhb")
            url("https://github.com/spring-projects/spring-loaded/blob/master/springloaded/src/main/java/org/springsource/loaded/support/ConcurrentWeakIdentityHashMap.java")
        }
    }
}

tasks.jar.get().apply {
    manifest {
        // https://docs.oracle.com/javase/tutorial/deployment/jar/packageman.html
        attributes["Name"] = Extras.name

        attributes["Specification-Title"] = Extras.name
        attributes["Specification-Version"] = Extras.version
        attributes["Specification-Vendor"] = Extras.vendor

        attributes["Implementation-Title"] = "${Extras.group}.${Extras.id}"
        attributes["Implementation-Version"] = Extras.buildDate
        attributes["Implementation-Vendor"] = Extras.vendor
    }
}

dependencies {
    api("com.dorkbox:Updates:1.1")
}

publishToSonatype {
    groupId = Extras.group
    artifactId = Extras.id
    version = Extras.version

    name = Extras.name
    description = Extras.description
    url = Extras.url

    vendor = Extras.vendor
    vendorUrl = Extras.vendorUrl

    issueManagement {
        url = "${Extras.url}/issues"
        nickname = "Gitea Issues"
    }

    developer {
        id = "dorkbox"
        name = Extras.vendor
        email = "email@dorkbox.com"
    }
}
