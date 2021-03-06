/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gridgain.scalar.tests

import org.apache.ignite.events.{IgniteEventType, IgniteEvent}
import org.apache.ignite.lang.IgnitePredicate
import org.gridgain.scalar._
import scalar._
import org.scalatest.matchers._
import org.scalatest._
import junit.JUnitRunner
import IgniteEventType._
import collection.JavaConversions._
import org.junit.runner.RunWith

/**
 * Scalar cache test.
 */
@RunWith(classOf[JUnitRunner])
class ScalarCacheSpec extends FlatSpec with ShouldMatchers {
    behavior of "Scalar cache"

    it should "work properly via Java APIs" in {
        scalar("examples/config/example-cache.xml") {
            registerListener()

            val c = cache$("partitioned").get.viewByType(classOf[Int], classOf[Int])

            c.putx(1, 1)
            c.putx(2, 2)

            c.values foreach println

            println("Size is: " + c.size)
        }
    }

    /**
     * This method will register listener for cache events on all nodes,
     * so we can actually see what happens underneath locally and remotely.
     */
    def registerListener() {
        val g = grid$

        g *< (() => {
            val lsnr = new IgnitePredicate[IgniteEvent]() {
                override def apply(e: IgniteEvent): Boolean = {
                    println(e.shortDisplay)

                    true
                }
            }

            if (g.cluster().nodeLocalMap[String, AnyRef].putIfAbsent("lsnr", lsnr) == null) {
                g.events.localListen(lsnr,
                    EVT_CACHE_OBJECT_PUT,
                    EVT_CACHE_OBJECT_READ,
                    EVT_CACHE_OBJECT_REMOVED)

                println("Listener is registered.")
            }
        }, null)
    }
}
