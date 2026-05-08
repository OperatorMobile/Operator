#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

static const char *base_name(const char *path) {
    const char *slash = strrchr(path, '/');
    return slash ? slash + 1 : path;
}

int main(int argc, char **argv) {
    const char *node_home = getenv("NODE_HOME");
    const char *tool = base_name(argv[0]);
    const char *script_name = strstr(tool, "npx") ? "npx-cli.js" : "npm-cli.js";
    if (!node_home || !node_home[0]) {
        fprintf(stderr, "%s: NODE_HOME is not set\n", tool);
        return 127;
    }

    size_t script_len = strlen(node_home) + strlen("/lib/node_modules/npm/bin/") + strlen(script_name) + 1;
    char *script = (char *)malloc(script_len);
    if (!script) {
        perror("malloc");
        return 127;
    }
    snprintf(script, script_len, "%s/lib/node_modules/npm/bin/%s", node_home, script_name);

    char **next_argv = (char **)calloc((size_t)argc + 2, sizeof(char *));
    if (!next_argv) {
        perror("calloc");
        free(script);
        return 127;
    }
    next_argv[0] = "node";
    next_argv[1] = script;
    for (int index = 1; index < argc; index++) {
        next_argv[index + 1] = argv[index];
    }

    execvp("node", next_argv);
    fprintf(stderr, "%s: failed to exec node: %s\n", tool, strerror(errno));
    return 127;
}
