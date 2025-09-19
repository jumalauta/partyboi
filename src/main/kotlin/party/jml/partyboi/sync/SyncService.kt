package party.jml.partyboi.sync

import arrow.core.raise.either
import arrow.core.right
import party.jml.partyboi.AppServices
import party.jml.partyboi.Service
import party.jml.partyboi.sync.SyncRepository.Companion.CLIENT_GROUP_ID
import party.jml.partyboi.sync.SyncRepository.Companion.ROOT_SERVER_GROUP_ID
import party.jml.partyboi.system.AppResult

class SyncService(app: AppServices) : Service(app) {
    private val repository = SyncRepository(app)

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

    suspend fun getHosts(): AppResult<List<NodeHost>> = repository.getHosts()

    suspend fun configureMaster() = either {
        val partyToCloudLink = NodeGroupLink(
            source = clientNodeGroup,
            target = serverNodeGroup,
            action = DataEventAction.Push,
        )

        val cloudToPartyLink = NodeGroupLink(
            source = serverNodeGroup,
            target = clientNodeGroup,
            action = DataEventAction.WaitForPull,
        )

        val partyToCloudRouter = DefaultRouter(
            id = "party-to-cloud",
            source = clientNodeGroup,
            target = serverNodeGroup,
        )

        val cloudToPartyRouter = DefaultRouter(
            id = "cloud-to-party",
            source = serverNodeGroup,
            target = clientNodeGroup,
        )

        val dataChannel = Channel(
            id = "database",
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
                channel = dataChannel
            )
        }

        repository.addNodeGroup(serverNodeGroup).bind()
        repository.addNodeGroup(clientNodeGroup).bind()
        repository.addNodeGroupLink(partyToCloudLink).bind()
        repository.addNodeGroupLink(cloudToPartyLink).bind()
        repository.addDefaultRouter(partyToCloudRouter).bind()
        repository.addDefaultRouter(cloudToPartyRouter).bind()
        repository.addChannel(dataChannel).bind()
        dataTriggers.forEach { trigger ->
            repository.addTrigger(trigger).bind()
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

    companion object {
        val serverNodeGroup = NodeGroup(
            id = ROOT_SERVER_GROUP_ID,
            description = "Root server instance running",
        )

        val clientNodeGroup = NodeGroup(
            id = CLIENT_GROUP_ID,
            description = "An instance running at the party place",
        )
    }
}

enum class SymmetricDsConfigurationState {
    MISSING,
    NOT_CONFIGURED,
    READY,
}