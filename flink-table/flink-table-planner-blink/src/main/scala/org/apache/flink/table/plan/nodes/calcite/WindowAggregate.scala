/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.table.plan.nodes.calcite

import org.apache.flink.table.calcite.FlinkRelBuilder.PlannerNamedWindowProperty
import org.apache.flink.table.calcite.FlinkTypeFactory
import org.apache.flink.table.plan.logical.LogicalWindow

import com.google.common.collect.ImmutableList
import org.apache.calcite.plan.{RelOptCluster, RelTraitSet}
import org.apache.calcite.rel.`type`.RelDataType
import org.apache.calcite.rel.core.{Aggregate, AggregateCall}
import org.apache.calcite.rel.{RelNode, RelShuttle, RelWriter}
import org.apache.calcite.util.ImmutableBitSet

import java.util

/**
  * Relational operator that eliminates duplicates and computes totals with time window group.
  *
  * NOTES: complex group (GROUPING SETS, CUBE, ROLLUP) is not supported now
  */
abstract class WindowAggregate(
    cluster: RelOptCluster,
    traitSet: RelTraitSet,
    child: RelNode,
    groupSet: ImmutableBitSet,
    aggCalls: util.List[AggregateCall],
    window: LogicalWindow,
    namedProperties: Seq[PlannerNamedWindowProperty])
  extends Aggregate(
    cluster,
    traitSet,
    child,
    groupSet,
    ImmutableList.of(groupSet),
    aggCalls) {

  def getWindow: LogicalWindow = window

  def getNamedProperties: Seq[PlannerNamedWindowProperty] = namedProperties

  override def accept(shuttle: RelShuttle): RelNode = shuttle.visit(this)

  override def deriveRowType(): RelDataType = {
    val aggregateRowType = super.deriveRowType()
    val typeFactory = getCluster.getTypeFactory.asInstanceOf[FlinkTypeFactory]
    val builder = typeFactory.builder
    builder.addAll(aggregateRowType.getFieldList)
    namedProperties.foreach { namedProp =>
      builder.add(
        namedProp.name,
        typeFactory.createFieldTypeFromLogicalType(namedProp.property.resultType)
      )
    }
    builder.build()
  }

  /**
    * The [[getDigest]] should be uniquely identifies the node; another node
    * is equivalent if and only if it has the same value. The [[getDigest]] is
    * computed by [[explainTerms(pw)]], so it should contains window information
    * to identify different WindowAggregate nodes, otherwise WindowAggregate node
    * can be replaced by any other WindowAggregate node.
    */
  override def explainTerms(pw: RelWriter): RelWriter = {
    super.explainTerms(pw)
      .item("window", window)
      .item("properties", namedProperties.map(_.name).mkString(", "))
  }
}
