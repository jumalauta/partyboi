package party.jml.partyboi.sync

import arrow.core.raise.either
import arrow.core.right
import party.jml.partyboi.AppServices
import party.jml.partyboi.Service
import party.jml.partyboi.sync.SyncRepository.Companion.CLIENT_GROUP_ID
import party.jml.partyboi.sync.SyncRepository.Companion.MASTER_GROUP_ID
import party.jml.partyboi.system.AppResult

class SyncService(app: AppServices) : Service(app) {
    private val repository = SyncRepository(app)
    val nodeName = "${app.config.dbDatabase}-master"

    private val tables = listOf(
        "appuser",
        "compo",
        "entry",
        "file",
        "event",
        "message",
        "property",
        "slideset",
        "screen",
        "trigger",
        "vote",
        "votekey",
    )

    suspend fun configurationState(): AppResult<SymmetricDsConfigurationState> = either {
        if (!repository.symmetricDsIsInstalled().bind()) {
            return SymmetricDsConfigurationState.MISSING.right()
        }
        return if (repository.symmetricDsIsConfigured().bind()) {
            SymmetricDsConfigurationState.READY.right()
        } else {
            SymmetricDsConfigurationState.NOT_CONFIGURED.right()
        }
    }

    suspend fun getHost(): AppResult<NodeHost> = either {
        val tz = app.time.timeZone.get().bind()
        repository.getHost(nodeName).bind().withTimezone(tz)
    }

    suspend fun configureMaster() = either {
        val masterNode = Node(
            id = nodeName,
            group = masterNodeGroup,
            externalId = nodeName,
            syncEnabled = true
        )

        val clientToMasterGroupLink = NodeGroupLink(
            source = clientNodeGroup,
            target = masterNodeGroup,
            action = DataEventAction.Push,
        )

        val masterToClientGroupLink = NodeGroupLink(
            source = masterNodeGroup,
            target = clientNodeGroup,
            action = DataEventAction.WaitForPull,
        )

        val clientToMasterRouter = DefaultRouter(
            id = "party-to-cloud",
            source = clientNodeGroup,
            target = masterNodeGroup,
        )

        val masterToClientRouter = DefaultRouter(
            id = "cloud-to-party",
            source = masterNodeGroup,
            target = clientNodeGroup,
        )

        val psqlDataChannel = Channel(
            id = "partyboi-db",
            processingOrder = 10,
            maxBatchSize = 1000,
            maxBatchToSend = 10,
            extractPeriodMillis = 0,
            batchAlgorithm = BatchAlgorithm.Default,
            enabled = true,
            fileSync = false,
            description = "Database tables: ${tables.joinToString(", ")}",
        )

        val dataTriggers = tables.map { table ->
            Trigger(
                id = table,
                sourceTable = table,
                channel = psqlDataChannel
            )
        }

        val triggerRouters = dataTriggers.flatMap { trigger ->
            listOf(
                TriggerRouter(
                    trigger = trigger,
                    router = masterToClientRouter,
                    enabled = true,
                    initialLoadOrder = 1,
                    pingBackEnabled = false,
                ),
                TriggerRouter(
                    trigger = trigger,
                    router = clientToMasterRouter,
                    enabled = true,
                    initialLoadOrder = 1,
                    pingBackEnabled = false,
                ),
            )
        }.mapIndexed { idx, trigger -> trigger.copy(initialLoadOrder = idx + 1) }

        repository.addNodeGroup(masterNodeGroup).bind()
        repository.addNodeGroup(clientNodeGroup).bind()
        repository.addNodeGroupLink(clientToMasterGroupLink).bind()
        repository.addNodeGroupLink(masterToClientGroupLink).bind()
        repository.addDefaultRouter(clientToMasterRouter).bind()
        repository.addDefaultRouter(masterToClientRouter).bind()
        repository.addChannel(psqlDataChannel).bind()
        dataTriggers.forEach { trigger ->
            repository.addTrigger(trigger).bind()
        }
        triggerRouters.forEach { router ->
            repository.addTriggerRouter(router).bind()
        }
    }

    suspend fun addClientNode(clientId: String, password: String) = either {
        val node = Node(
            id = clientId,
            externalId = clientId,
            group = clientNodeGroup,
        )

        val security = NodeSecurity(
            node = node,
            nodePassword = password,
        )

        repository.addNode(node).bind()
        repository.addNodeSecurity(security).bind()
    }

    suspend fun getClientNodeSecurities(): AppResult<List<NodeSecurity>> =
        repository.getNodeSecurities(CLIENT_GROUP_ID)

    suspend fun setSyncEnabled(nodeId: String, enabled: Boolean): AppResult<Unit> =
        repository.setSyncEnabled(nodeId, enabled)

    companion object {
        val masterNodeGroup = NodeGroup(
            id = MASTER_GROUP_ID,
            description = "A node running on cloud",
        )

        val clientNodeGroup = NodeGroup(
            id = CLIENT_GROUP_ID,
            description = "A node running at the party place",
        )
    }
}

enum class SymmetricDsConfigurationState {
    MISSING,
    NOT_CONFIGURED,
    READY,
}