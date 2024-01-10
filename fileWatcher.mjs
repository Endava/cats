import chokidar from 'chokidar';
import { exec } from 'child_process';
import path from 'path';
import { fileURLToPath } from 'url';
import fs from 'fs';
import gitignoreParser from 'gitignore-parser';

// Get the current directory
const currentDir = path.dirname(fileURLToPath(import.meta.url));

// Specify the directory to watch
const directoryToWatch = path.join(currentDir, '.');

// Path to .gitignore file
const gitignorePath = path.join(currentDir, '.gitignore');

// Initialize ignore function
let ignoreFunc = () => false;

// Check if .gitignore file exists
if (fs.existsSync(gitignorePath)) {
    // Read .gitignore file
    const gitignoreContent = fs.readFileSync(gitignorePath).toString();

    // Parse .gitignore content
    const gitignore = gitignoreParser.compile(gitignoreContent);

    // Update ignore function
    ignoreFunc = (filePath) => {
        const relativePath = path.relative(currentDir, filePath);
        return gitignore.denies(relativePath);
    };
}

// Initialize watcher
const watcher = chokidar.watch(directoryToWatch, {
    ignored: ignoreFunc,
    persistent: true,
});


// On change event
watcher.on('change', (filePath) => {
    console.log(`File ${filePath} has been changed. Running command...`);

    // Specify the first command to run
    const command1 = './mvnw package -e -Dquarkus.package.type=uber-jar';

    // Execute the first command
    exec(command1, { maxBuffer: 1024 * 50000 }, (error, stdout, stderr) => {
        if (error) {
            console.error(`Error executing command: ${error}`);
            return;
        }
        console.log(`Command output: ${stdout}`);
        console.log(`Command log output: ${stderr}`);
    });
});

console.log(`Watching for changes in directory: ${directoryToWatch}`);
