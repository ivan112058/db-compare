package com.zxqj.dbcompare.controller

import org.springframework.web.bind.annotation.*
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

@RestController
@RequestMapping("/api/git")
@CrossOrigin(origins = ["*"])
class GitController {

    @GetMapping("/status")
    fun getGitStatus(@RequestParam path: String): GitStatus {
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) {
            return GitStatus(error = "Invalid directory")
        }

        try {
            // Check if it's a git repo
            if (!File(dir, ".git").exists()) {
                return GitStatus(error = "Not a git repository")
            }

            // Get branch name
            val branchProcess = ProcessBuilder("git", "rev-parse", "--abbrev-ref", "HEAD")
                .directory(dir)
                .start()
            val branch = branchProcess.inputStream.bufferedReader().readText().trim()
            
            // Check dirty status
            val statusProcess = ProcessBuilder("git", "status", "--porcelain")
                .directory(dir)
                .start()
            val statusOutput = statusProcess.inputStream.bufferedReader().readText()
            val isDirty = statusOutput.isNotBlank()

            return GitStatus(branch = branch, isDirty = isDirty)
        } catch (e: Exception) {
            return GitStatus(error = e.message)
        }
    }

    @GetMapping("/root")
    fun findGitRoot(@RequestParam path: String): String {
        var dir = File(path)
        if (dir.exists() && dir.isFile) {
            dir = dir.parentFile
        }
        
        // Safety check: don't go too far up
        val home = File(System.getProperty("user.home"))
        
        var current: File? = dir
        while (current != null && current.exists()) {
            if (File(current, ".git").exists()) {
                return current.absolutePath
            }
            if (current == home || current.parentFile == null) {
                break
            }
            current = current.parentFile
        }
        
        // If not found, return the parent directory of the original path (if file) or the path itself (if dir)
        return dir.absolutePath
    }

    data class GitStatus(
        val branch: String = "",
        val isDirty: Boolean = false,
        val error: String? = null
    )
}
