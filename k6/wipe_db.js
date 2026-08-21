const { MongoClient } = require('mongodb');

const MONGO_URI = 'mongodb+srv://kavish_1121:ZcYO8exJOAqpqi7V@cluster0.cvw1r6l.mongodb.net/jobscheduler?retryWrites=true&w=majority&appName=Cluster0';

async function wipe() {
    console.log(`Connecting to MongoDB...`);
    const client = new MongoClient(MONGO_URI);

    try {
        await client.connect();
        const db = client.db('jobscheduler');

        console.log(`Clearing jobs collection...`);
        const jobsResult = await db.collection('jobs').deleteMany({});
        console.log(`Deleted ${jobsResult.deletedCount} jobs.`);

        console.log(`Clearing execution logs collection...`);
        const logsResult = await db.collection('execution_logs').deleteMany({});
        console.log(`Deleted ${logsResult.deletedCount} execution logs.`);

        console.log(`Wipe complete!`);
    } catch (err) {
        console.error('Error wiping database:', err);
    } finally {
        await client.close();
    }
}

wipe();
