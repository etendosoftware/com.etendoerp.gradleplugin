String getCommit(String directory = null) {
    String changeDir = directory ? "cd ${directory} && " : ''
    def command = "${changeDir}git log -1 --format=format:'%H'"
    return sh(returnStdout: true, script: command).trim()
}

String getCommitDate(String directory = null) {
    String changeDir = directory ? "cd ${directory} && " : ''
    def command = "${changeDir}git log -1 --date=format:'%d-%m-%Y %H:%M:%S' --pretty='%ad'"
    return sh(returnStdout: true, script: command).trim()
}

String getCommitAuthorName(String directory = null) {
    String changeDir = directory ? "cd ${directory} && " : ''
    def command = "${changeDir}git log -1 --pretty=format:'%an'"
    return sh(returnStdout: true, script: command).trim()
}

String getCommitAuthorEmail(String directory = null) {
    String changeDir = directory ? "cd ${directory} && " : ''
    def command = "${changeDir}git log -1 --pretty=format:'%ae'"
    return sh(returnStdout: true, script: command).trim()
}

String generateCommitInfo(String repoUrl, String repoGroup, String packageName, boolean includeAuthor, String directory) {
    def commit = getCommit(directory)
    def commitDate = getCommitDate(directory)
    def formattedRepoUrl = (repoGroup == null || repoGroup.isEmpty()) ? "${repoUrl}/${packageName}" : "${repoUrl}/${repoGroup}.${packageName}"

    def urlSuffix = repoUrl.contains("bitbucket") ? "commits" : "commit"
    def commitUrl = "${formattedRepoUrl}/${urlSuffix}/${commit}"

    def commitInfo = "<em>⇢ ${packageName}:</em>\n" +
        "<ul>\n" +
        "<li><strong>Last Commit:</strong> ${commitUrl}</li>\n"
    
    if (includeAuthor) {
        def commitAuthorName = getCommitAuthorName(directory)
        def commitAuthorEmail = getCommitAuthorEmail(directory)
        commitInfo += "<li><strong>Author:</strong> ${commitAuthorName} (${commitAuthorEmail})</li>\n"
    }

    commitInfo += "<li><strong>Date:</strong> ${commitDate}</li>\n</ul>"

    return commitInfo
}

return this