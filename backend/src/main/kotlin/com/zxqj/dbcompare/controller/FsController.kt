package com.zxqj.dbcompare.controller

import org.springframework.web.bind.annotation.*
import java.io.File

@RestController
@RequestMapping("/api/fs")
@CrossOrigin(origins = ["*"])
class FsController {

    @GetMapping("/list")
    fun listFiles(@RequestParam(required = false) path: String?): List<FileItem> {
        var dir = if (path.isNullOrEmpty()) File(System.getProperty("user.home")) else File(path)
        
        // If path is a file, use its parent directory
        if (dir.exists() && dir.isFile) {
            dir = dir.parentFile
        }

        if (!dir.exists() || !dir.isDirectory) {
            return emptyList()
        }

        val files = dir.listFiles() ?: return emptyList()
        
        // Sort: Directories first, then files. Both alphabetically.
        return files.map { 
            FileItem(
                name = it.name,
                path = it.absolutePath,
                isDirectory = it.isDirectory
            )
        }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }
    
    @GetMapping("/parent")
    fun getParent(@RequestParam path: String): FileItem? {
        val file = File(path)
        val parent = file.parentFile ?: return null
        return FileItem(parent.name, parent.absolutePath, true)
    }

    data class FileItem(
        val name: String,
        val path: String,
        val isDirectory: Boolean
    )
}
