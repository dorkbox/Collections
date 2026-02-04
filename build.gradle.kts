/*
 * Copyright 2026 dorkbox, llc
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

gradle.startParameter.showStacktrace = ShowStacktrace.ALWAYS   // always show the stacktrace!
gradle.startParameter.warningMode = WarningMode.All

plugins {
    id("com.dorkbox.GradleUtils") version "4.8"
    id("com.dorkbox.Licensing") version "3.1"
    id("com.dorkbox.VersionUpdate") version "3.2"
    id("com.dorkbox.GradlePublish") version "2.2"

    kotlin("jvm") version "2.3.0"
}


GradleUtils.load {
    group = "com.dorkbox"
    id = "Collections" // this is the maven ID!

    description = "Collection types and utilities to enhance the default collections"
    name = "Collections"
    version = "2.9"

    vendor = "Dorkbox LLC"
    vendorUrl = "https://dorkbox.com"

    url = "https://git.dorkbox.com/dorkbox/Collections"

    issueManagement {
        url = "${url}/issues"
        nickname = "Gitea Issues"
    }

    developer {
        id = "dorkbox"
        name = vendor
        email = "email@dorkbox.com"
    }
}
GradleUtils.defaults()
GradleUtils.compileConfiguration(JavaVersion.VERSION_25)


licensing {
    license(License.APACHE_2) {
        description(Extras.description)
        author(Extras.vendor)
        url(Extras.url)

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
    }
}


dependencies {
    api("com.dorkbox:Updates:1.3")

    testImplementation("junit:junit:4.13.2")
}
