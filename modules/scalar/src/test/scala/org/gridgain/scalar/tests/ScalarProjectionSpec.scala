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

import org.apache.ignite.Ignition
import org.apache.ignite.cluster.ClusterNode
import org.apache.ignite.configuration.IgniteConfiguration
import org.apache.ignite.messaging.MessagingListenActor
import org.gridgain.scalar._
import scalar._
import org.scalatest.matchers._
import org.scalatest._
import junit.JUnitRunner
import org.apache.ignite._
import org.gridgain.grid._
import collection.JavaConversions._
import java.util.UUID
import org.junit.runner.RunWith

/**
 * Scalar cache test.
 */
@RunWith(classOf[JUnitRunner])
class ScalarProjectionSpec extends FlatSpec with ShouldMatchers with BeforeAndAfterAll {
    /**
     *
     */
    override def beforeAll() {
        Ignition.start(gridConfig("node-1", false))
        Ignition.start(gridConfig("node-2", true))
    }

    /**
     *
     */
    override def afterAll() {
        Ignition.stop("node-1", true)
        Ignition.stop("node-2", true)
    }

    /**
     *
     * @param name Grid name.
     * @param shown Shown flag.
     */
    private def gridConfig(name: String, shown: Boolean): IgniteConfiguration = {
        val attrs: java.util.Map[String, Boolean] = Map[String, Boolean]("shown" -> shown)

        val cfg = new IgniteConfiguration

        cfg.setGridName(name)
        cfg.setUserAttributes(attrs)

        cfg
    }

    behavior of "ScalarProjectionPimp class"

    it should "return all nodes" in scalar(gridConfig("node-scalar", true)) {
        assertResult(3) {
            grid$("node-scalar").get.cluster().nodes().size
        }
    }

    it should "return shown nodes" in  scalar(gridConfig("node-scalar", true)) {
        assert(grid$("node-scalar").get.nodes$((node: ClusterNode) => node.attribute[Boolean]("shown")).size == 2)
    }

    it should "return all remote nodes" in scalar(gridConfig("node-scalar", true)) {
        assertResult(2) {
            grid$("node-scalar").get.remoteNodes$().size
        }
    }

    it should "return shown remote nodes" in  scalar(gridConfig("node-scalar", true)) {
        assert(grid$("node-scalar").get.remoteNodes$((node: ClusterNode) =>
            node.attribute[Boolean]("shown")).size == 1)
    }

    it should "correctly send messages" in scalar(gridConfig("node-scalar", true)) {

        grid$("node-1").get.message().remoteListen(null, new MessagingListenActor[Any]() {
            def receive(nodeId: UUID, msg: Any) {
                println("node-1 received " + msg)
            }
        })

        grid$("node-2").get.message().remoteListen(null, new MessagingListenActor[Any]() {
            def receive(nodeId: UUID, msg: Any) {
                println("node-2 received " + msg)
            }
        })

        grid$("node-scalar").get !< ("Message", null)
        grid$("node-scalar").get !< (Seq("Message1", "Message2"), null)
    }

    it should "correctly make calls" in scalar(gridConfig("node-scalar", true)) {
        println("CALL RESULT: " + grid$("node-scalar").get #< (() => "Message", null))

        println("ASYNC CALL RESULT: " + grid$("node-scalar").get.callAsync$[String](() => "Message", null).get)

        val call1: () => String = () => "Message1"
        val call2: () => String = () => "Message2"

        println("MULTIPLE CALL RESULT: " + grid$("node-scalar").get #< (Seq(call1, call2), null))

        println("MULTIPLE ASYNC CALL RESULT: " +
            (grid$("node-scalar").get #? (Seq(call1, call2), null)).get)
    }

    it should "correctly make runs" in scalar(gridConfig("node-scalar", true)) {
        grid$("node-scalar").get *< (() => println("RUN RESULT: Message"), null)

        (grid$("node-scalar").get *? (() => println("ASYNC RUN RESULT: Message"), null)).get

        val run1: () => Unit = () => println("RUN 1 RESULT: Message1")
        val run2: () => Unit = () => println("RUN 2 RESULT: Message2")

        grid$("node-scalar").get *< (Seq(run1, run2), null)

        val runAsync1: () => Unit = () => println("ASYNC RUN 1 RESULT: Message1")
        val runAsync2: () => Unit = () => println("ASYNC RUN 2 RESULT: Message2")

        (grid$("node-scalar").get *? (Seq(runAsync1, runAsync2), null)).get
    }

    it should "correctly reduce" in scalar(gridConfig("node-scalar", true)) {
        val call1: () => Int = () => 15
        val call2: () => Int = () => 82

        assert(grid$("node-scalar").get @< (Seq(call1, call2), (n: Seq[Int]) => n.sum, null) == 97)
        assert(grid$("node-scalar").get.reduceAsync$(Seq(call1, call2), (n: Seq[Int]) => n.sum, null).get == 97)
    }
}
