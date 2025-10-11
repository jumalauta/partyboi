package party.jml.partyboi.sync

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toKotlinInstant
import kotliquery.Row
import party.jml.partyboi.AppServices
import party.jml.partyboi.Service
import party.jml.partyboi.data.toBoolean
import party.jml.partyboi.data.toInt
import party.jml.partyboi.db.*
import party.jml.partyboi.db.DbBasicMappers.asBoolean
import party.jml.partyboi.system.AppResult
import party.jml.partyboi.system.utcToTimeZone

class SyncRepository(app: AppServices) : Service(app) {
    private val db = app.db

    suspend fun symmetricDsIsInstalled(): AppResult<Boolean> = db.use {
        it.one(
            queryOf(
                """
            SELECT count(*) > 40
            FROM pg_catalog.pg_tables
            WHERE tablename LIKE 'sym_%'
        """.trimIndent()
            ).map(asBoolean)
        )
    }

    suspend fun symmetricDsIsConfigured(): AppResult<Boolean> = db.use {
        it.one(
            queryOf(
                """
            SELECT count(*) = 2
            FROM sym_node_group
            WHERE node_group_id = ANY ('{$MASTER_GROUP_ID, $CLIENT_GROUP_ID}');
        """.trimIndent()
            ).map(asBoolean)
        )
    }
    
    suspend fun getHost(hostId: String): AppResult<NodeHost> = db.use {
        it.one(queryOf("SELECT * FROM sym_node_host WHERE node_id = ?", hostId).map(NodeHost.fromRow))
    }

    suspend fun addNodeGroup(group: NodeGroup) = db.use {
        it.updateAny(
            queryOf(
                """
                    INSERT INTO sym_node_group (node_group_id, description)
                    VALUES (?, ?)
                    ON CONFLICT DO NOTHING
                """.trimIndent(),
                group.id,
                group.description
            ),
        )
    }

    suspend fun addNodeGroupLink(link: NodeGroupLink) = db.use {
        it.updateAny(
            queryOf(
                """
                INSERT INTO sym_node_group_link (source_node_group_id, target_node_group_id, data_event_action)
                VALUES (?, ?, ?)
                ON CONFLICT DO NOTHING
            """.trimIndent(),
                link.source.id,
                link.target.id,
                link.action.value,
            )
        )
    }

    suspend fun addDefaultRouter(router: DefaultRouter) = db.use {
        it.updateAny(
            queryOf(
                """
                INSERT INTO sym_router 
                    (router_id, source_node_group_id, target_node_group_id, create_time, last_update_time)
                VALUES (?, ?, ?, now(), now())
                ON CONFLICT DO NOTHING
            """.trimIndent(),
                router.id,
                router.source.id,
                router.target.id,
            )
        )
    }

    suspend fun addChannel(channel: Channel) = db.use {
        it.updateAny(
            queryOf(
                """
                INSERT INTO sym_channel
                    (channel_id, processing_order, max_batch_size, max_batch_to_send,
                    extract_period_millis, batch_algorithm, enabled, file_sync_flag, description)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT DO NOTHING
            """.trimIndent(),
                channel.id,
                channel.processingOrder,
                channel.maxBatchSize,
                channel.maxBatchToSend,
                channel.extractPeriodMillis,
                channel.batchAlgorithm.value,
                channel.enabled.toInt(),
                channel.fileSync.toInt(),
                channel.description,
            )
        )
    }

    suspend fun addTrigger(trigger: Trigger) = db.use {
        it.updateAny(
            queryOf(
                """
                INSERT INTO sym_trigger
                    (trigger_id, source_table_name, channel_id, last_update_time, create_time)
                VALUES (?, ?, ?, now(), now())
                ON CONFLICT DO NOTHING
            """.trimIndent(),
                trigger.id,
                trigger.sourceTable,
                trigger.channel.id,
            )
        )
    }

    suspend fun addTriggerRouter(router: TriggerRouter) = db.use {
        it.updateAny(
            queryOf(
                """
                INSERT INTO sym_trigger_router
                    (trigger_id, router_id, enabled, initial_load_order, ping_back_enabled, create_time, last_update_time)
                VALUES (?, ?, ?, ?, ?, now(), now())
                ON CONFLICT DO NOTHING
            """.trimIndent(),
                router.trigger.id,
                router.router.id,
                router.enabled.toInt(),
                router.initialLoadOrder,
                router.pingBackEnabled.toInt(),
            )
        )
    }

    suspend fun addNode(node: Node) = db.use {
        it.updateAny(
            queryOf(
                """
                INSERT INTO sym_node
                    (node_id, node_group_id, external_id, sync_enabled)
                VALUES (?, ?, ?, ?)
                ON CONFLICT DO NOTHING
            """.trimIndent(),
                node.id,
                node.group.id,
                node.externalId,
                node.syncEnabled.toInt(),
            )
        )
    }

    suspend fun getNodeSecurities(nodeGroupId: String) = db.use {
        it.many(
            queryOf(
                """
            SELECT *
            FROM sym_node_security
            JOIN sym_node ON sym_node.node_id = sym_node_security.node_id
            JOIN sym_node_group ON sym_node.node_group_id = sym_node_group.node_group_id
            WHERE sym_node.node_group_id = ?
            ORDER BY sym_node_security.node_id
        """.trimIndent(), nodeGroupId
            ).map(NodeSecurity.fromRow)
        )
    }

    suspend fun addNodeSecurity(security: NodeSecurity) = db.use {
        it.updateAny(
            queryOf(
                """
                INSERT INTO sym_node_security
                    (node_id, node_password, registration_enabled, initial_load_enabled)
                VALUES (?, ?, ?, ?)
                ON CONFLICT DO NOTHING
            """.trimIndent(),
                security.node.id,
                security.nodePassword,
                security.registrationEnabled.toInt(),
                security.initialLoadEnabled.toInt(),
            )
        )
    }

    suspend fun setSyncEnabled(nodeId: String, enabled: Boolean): AppResult<Unit> = db.use {
        it.updateOne(
            queryOf(
                """
            UPDATE sym_node
            SET sync_enabled = ? 
            WHERE node_id = ?
        """.trimIndent(),
                enabled.toInt(),
                nodeId
            )
        )
    }

    companion object {
        const val MASTER_GROUP_ID = "master"
        const val CLIENT_GROUP_ID = "client"
    }
}

data class NodeGroup(
    val id: String,
    val description: String?,
)

data class NodeGroupLink(
    val source: NodeGroup,
    val target: NodeGroup,
    val action: DataEventAction,
)

enum class DataEventAction(val value: String) {
    /**
     * Indicates that nodes in the source node group will initiate communication over an HTTP PUT and push data to nodes
     * in the target node group.
     */
    Push("P"),

    /**
     * Indicates nodes in the source node group will wait for a node in the target node group to connect via an HTTP GET
     * and allow the nodes in the target node group to pull data from the nodes in the source node group.
     */
    WaitForPull("W"),

    /**
     * Route-only indicates that the data isn’t exchanged between nodes in the source and nodes in the target node
     * groups via SymmetricDS. This action type might be useful when using an XML publishing router or an audit table
     * changes router.
     */
    RouteOnly("R"),
}

data class DefaultRouter(
    val id: String,
    val source: NodeGroup,
    val target: NodeGroup,
)

data class Channel(
    val id: String,
    val processingOrder: Int,
    val maxBatchSize: Int,
    val maxBatchToSend: Int,
    val extractPeriodMillis: Long,
    val batchAlgorithm: BatchAlgorithm,
    val enabled: Boolean,
    val fileSync: Boolean,
    val description: String,
)

enum class BatchAlgorithm(val value: String) {
    /**
     * All changes that happen in a transaction are guaranteed to be batched together. Multiple transactions will be
     * batched and committed together until there is no more data to be sent or the max_batch_size is reached.
     */
    Default("default"),

    /**
     * Batches will map directly to database transactions. If there are many small database transactions, then there
     * will be many batches. The max_batch_size column has no effect.
     */
    Transactional("transactional"),

    /**
     * Multiple transactions will be batched and committed together until there is no more data to be sent or the
     * max_batch_size is reached. The batch will be cut off at the max_batch_size regardless of whether it is in the
     * middle of a transaction.
     */
    Nontransactional("nontransactional"),
}

data class Trigger(
    val id: String,
    val sourceTable: String,
    val channel: Channel,
)

data class TriggerRouter(
    val trigger: Trigger,
    val router: DefaultRouter,
    val enabled: Boolean,
    val initialLoadOrder: Int,
    val pingBackEnabled: Boolean,
)

data class Node(
    val id: String,
    val group: NodeGroup,
    val externalId: String,
    val syncEnabled: Boolean = true,
) {
    companion object {
        val fromRow: (Row) -> Node = { row ->
            Node(
                id = row.string("node_id"),
                group = NodeGroup(
                    id = row.string("node_group_id"),
                    description = row.stringOrNull("description"),
                ),
                externalId = row.string("external_id"),
                syncEnabled = row.int("sync_enabled").toBoolean(),
            )
        }
    }
}

data class NodeSecurity(
    val node: Node,
    val nodePassword: String,
    val registrationEnabled: Boolean = true,
    val initialLoadEnabled: Boolean = true,
) {
    companion object {
        val fromRow: (Row) -> NodeSecurity = { row ->
            NodeSecurity(
                node = Node.fromRow(row),
                nodePassword = row.string("node_password"),
                registrationEnabled = row.int("registration_enabled").toBoolean(),
                initialLoadEnabled = row.int("initial_load_enabled").toBoolean(),
            )
        }
    }
}

data class NodeHost(
    val osName: String,
    val osArch: String,
    val osVersion: String,
    val availableProcessors: Int,
    val freeMemoryBytes: Long,
    val totalMemoryBytes: Long,
    val maxMemoryBytes: Long,
    val javaVersion: String,
    val javaVendor: String,
    val jdbcVersion: String,
    val symmetricVersion: String,
    val heartbeatTime: Instant,
    val lastRestartTime: Instant,
    val createTime: Instant,
) {
    fun withTimezone(tz: TimeZone): NodeHost = copy(
        heartbeatTime = heartbeatTime.utcToTimeZone(tz),
        lastRestartTime = lastRestartTime.utcToTimeZone(tz),
        createTime = createTime.utcToTimeZone(tz),
    )

    companion object {
        val fromRow: (Row) -> NodeHost = { row ->
            NodeHost(
                osName = row.string("os_name"),
                osArch = row.string("os_arch"),
                osVersion = row.string("os_version"),
                availableProcessors = row.int("available_processors"),
                freeMemoryBytes = row.long("free_memory_bytes"),
                totalMemoryBytes = row.long("total_memory_bytes"),
                maxMemoryBytes = row.long("max_memory_bytes"),
                javaVersion = row.string("java_version"),
                javaVendor = row.string("java_vendor"),
                jdbcVersion = row.string("jdbc_version"),
                symmetricVersion = row.string("symmetric_version"),
                heartbeatTime = row.instant("heartbeat_time").toKotlinInstant(),
                lastRestartTime = row.instant("last_restart_time").toKotlinInstant(),
                createTime = row.instant("create_time").toKotlinInstant(),
            )
        }
    }
}