AKka cluster sharding handover

For persistent actors akka persistence is very useful, using event sourcing to keep track of state.   
But in certain cases you might want to have an aggregated state in the actor which cannot be updated in real time.   
If the time needed to load the state is quite long then rebalancing shards/restarting nodes becomes quite cumberstone as the entities are not ready to serve responses until the response is received.   
Using the handover message to send the current state to the sharding region would reduce latency and pass the data from the old entity to the new one without adding the complexity of persistence or distributed data.   
This solution will stil require another persistent storage for the data but it's assumed that the state of the entity is sourced from other systems.
