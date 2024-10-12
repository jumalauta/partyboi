package party.jml.partyboi.assets

import party.jml.partyboi.AppServices
import party.jml.partyboi.Config
import party.jml.partyboi.form.FileUpload

class AssetsRepository(app: AppServices) {
    private val dir = Config.getAssetsDir()

    fun write(file: FileUpload) {
        val target = dir.resolve(file.name)
        target.toFile().mkdirs()
        file.write(target)
    }
}