const fs = require('fs');
const readline = require('readline');

const rl = readline.createInterface({
    input: fs.createReadStream('C:/Users/KAVISH/.gemini/antigravity-ide/brain/dee64f3e-d48a-4ed4-87cd-d1a05a38366c/.system_generated/logs/transcript.jsonl'),
    crlfDelay: Infinity
});

async function processLineByLine() {
    for await (const line of rl) {
        if (!line.trim()) continue;
        try {
            const entry = JSON.parse(line);
            if (entry.type === 'USER_INPUT' || entry.type === 'PLANNER_RESPONSE' || entry.type === 'CHAT_RESPONSE') {
                console.log(`\n[${entry.source}] ${entry.type}:`);
                if (entry.content) {
                    console.log(entry.content.substring(0, 1500) + (entry.content.length > 1500 ? '... (truncated)' : ''));
                }
            }
        } catch (e) {
            // Ignore parse errors
        }
    }
}

processLineByLine();
