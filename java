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
