const fs = require('fs');

// 1. Load the parallel pipeline definition
const pipelineData = {
  "pipelineId": "bash-ai-parallel-exec",
  "agents": [
    { "id": "agent-01", "name": "Initial Splitter", "type": "orchestrator", "next": ["agent-02a", "agent-02b"] },
    { "id": "agent-02a", "name": "Parallel Bash Task A", "type": "bash-runner", "next": ["agent-03"] },
    { "id": "agent-02b", "name": "Parallel Bash Task B", "type": "bash-runner", "next": ["agent-03"] },
    { "id": "agent-03", "name": "Final Aggregator", "type": "summarizer", "next": [] }
  ]
};

// Simulated mock execution of an individual agent task
async function executeAgentTask(agent) {
  console.log(`[▶️ START] Executing: ${agent.name} (${agent.id})`);
  
  // Simulate varying processing times (Task B takes longer than Task A)
  const duration = agent.id === 'agent-02b' ? 2000 : 1000;
  await new Promise(resolve => setTimeout(resolve, duration));
  
  console.log(`[✅ DONE] Finished: ${agent.name} (${agent.id})`);
  return { id: agent.id, status: "success" };
}

// 2. Main Graph Processing Engine
async function runParallelPipeline(pipeline) {
  const agentsMap = new Map(pipeline.agents.map(a => [a.id, a]));
  
  // Track how many parent nodes each agent is waiting for
  const remainingDependencies = {};
  pipeline.agents.forEach(agent => {
    remainingDependencies[agent.id] = 0;
  });

  // Calculate total incoming lines (In-Degree) for every node
  pipeline.agents.forEach(agent => {
    agent.next.forEach(nextId => {
      if (remainingDependencies[nextId] !== undefined) {
        remainingDependencies[nextId]++;
      }
    });
  });

  // Find all entry-point nodes (nodes with 0 parent dependencies)
  const entryNodes = pipeline.agents.filter(a => remainingDependencies[a.id] === 0);
  
  if (entryNodes.length === 0) {
    console.error("❌ Error: Invalid graph configuration. Deadlock detected or no starting node.");
    return;
  }

  // Recursive queue executor to process active available nodes
  async function executeQueue(currentNodes) {
    if (currentNodes.length === 0) return;

    // Trigger all currently unblocked nodes concurrently using Promise.all
    const executionPromises = currentNodes.map(async (agent) => {
      await executeAgentTask(agent);
      
      const nextNodesToTrigger = [];

      // Process downstream children nodes
      agent.next.forEach(nextId => {
        remainingDependencies[nextId]--; // One parent completed
        
        // If all parent nodes are finished, this child is now unblocked
        if (remainingDependencies[nextId] === 0) {
          nextNodesToTrigger.push(agentsMap.get(nextId));
        }
      });

      // Recurse downstream for this specific branch chain
      await executeQueue(nextNodesToTrigger);
    });

    await Promise.all(executionPromises);
  }

  console.log(`🚀 Starting Execution Pipeline: ${pipeline.pipelineId}\n---`);
  await executeQueue(entryNodes);
  console.log(`---\n🎉 Pipeline Execution Completed Successfully.`);
}

// Run the script
runParallelPipeline(pipelineData);
const express = require('express');
const { execFile } = require('child_process');

const app = express();
app.use(express.json());

// Enable CORS so your Lovable frontend dashboard can reach this endpoint
app.use((req, res, next) => {
  res.header("Access-Control-Allow-Origin", "*");
  res.header("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept");
  next();
});

// 1. Safe Terminal Command Runner
function executeBashCommand(agentName, commandFile, commandArgs = []) {
  return new Promise((resolve) => {
    console.log(`[🔨 SPAWN] ${agentName} running: ${commandFile} ${commandArgs.join(' ')}`);
    
    // execFile runs binary/script targets within an isolated parameters scope
    execFile(commandFile, commandArgs, { timeout: 10000 }, (error, stdout, stderr) => {
      if (error) {
        console.error(`[❌ ERROR] ${agentName} failed:`, error.message);
        return resolve({ status: "failed", output: stderr || error.message });
      }
      
      console.log(`[✅ DONE] ${agentName} completed.`);
      return resolve({ status: "success", output: stdout.trim() });
    });
  });
}

// 2. Core Concurrent Graph Processing Engine
async function runEngine(pipeline) {
  const agentsMap = new Map(pipeline.agents.map(a => [a.id, a]));
  const executionLogs = {};
  const remainingDependencies = {};

  // Setup graph node incoming trackers
  pipeline.agents.forEach(agent => { remainingDependencies[agent.id] = 0; });
  pipeline.agents.forEach(agent => {
    agent.next.forEach(nextId => {
      if (remainingDependencies[nextId] !== undefined) remainingDependencies[nextId]++;
    });
  });

  const entryNodes = pipeline.agents.filter(a => remainingDependencies[a.id] === 0);
  if (entryNodes.length === 0) throw new Error("Deadlock or missing start node");

  async function executeQueue(currentNodes) {
    if (currentNodes.length === 0) return;

    const promises = currentNodes.map(async (agent) => {
      // Execute the real system command provided by the agent parameters
      // Default fallback runs a basic echo command if parameters are missing
      const cmd = agent.command || 'echo';
      const args = agent.args || [`Hello from ${agent.name}`];
      
      const result = await executeBashCommand(agent.name, cmd, args);
      executionLogs[agent.id] = result;

      const nextNodesToTrigger = [];
      agent.next.forEach(nextId => {
        remainingDependencies[nextId]--;
        if (remainingDependencies[nextId] === 0) {
          nextNodesToTrigger.push(agentsMap.get(nextId));
        }
      });

      await executeQueue(nextNodesToTrigger);
    });

    await Promise.all(promises);
  }

  await executeQueue(entryNodes);
  return executionLogs;
}

// 3. API Endpoint Definition
app.post('/api/pipeline/run', async (req, res) => {
  try {
    const pipelineData = req.body;
    
    if (!pipelineData || !Array.isArray(pipelineData.agents)) {
      return res.status(400).json({ error: "Invalid pipeline structure payload." });
    }

    const report = await runEngine(pipelineData);
    res.json({ status: "pipeline_completed", runs: report });

  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// Launch Server
const PORT = 5000;
app.listen(PORT, () => {
  console.log(`🤖 bash-ai Engine Active on http://localhost:${PORT}`);
});
fetch('http://localhost:5000/api/pipeline/run', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(currentPipelineSchema)
})
.then(res => res.json())
.then(data => updateUiConsole(data.runs));
