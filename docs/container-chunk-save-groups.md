# World block-container save groups

A native `NativeContainerTransaction.move` whose physical inventory backings all
belong to block entities in one LevelDB provider registers their chunk generations
before the inventory event or either setter. This includes both physical halves
of a double chest. The setters, validation, rollback and notification order stay
native. Registration does no database IO.

A previously preparing, queued, in-flight or unacknowledged independent chunk
snapshot prevents admission. The hopper retries with its normal cooldown. Open
groups merge transitively (A-B, B-C), and all members stay mounted until a common
write acknowledges them. A provisional mutation or capture cannot publish a
half-pair. Save futures acknowledge the covered revision; later dirty changes
remain dirty and cannot receive an older ACK. Cancelling one caller's future does
not cancel another caller's ACK.

Every provider save route captures the actual chunk state into one shared native
WriteBatch on the main thread and writes that batch on the serial worker. Entity
cleanup retains the union of actor IDs from all participants. Cleanup is buffered
before changing the retained batch; an unknown write result fences the complete
immutable snapshot and retries that snapshot rather than replaying an item move.
Old independent queued jobs can only observe already acknowledged empty slots.

Reads invalidate old admission tickets and request a group flush. Unload,
replacement and regeneration defer before moving or closing live holders.
Lifecycle reservations span callbacks so a new group cannot enter between a
first detach check and the actual detach. Provider, full-chunk and Level unload
paths preserve the loaded generation on refusal. `Level.close` rejects a
provisional mutation or capture before clearing Level state. Shutdown captures
open groups while Level is valid, then uses the existing bounded worker drain.
Only a closed provider may release holders before an unresolved ACK: it cannot
admit or mount another generation, and the worker already owns captured bytes.
Retained batches are released under the exclusive database-close lifetime lock.

`saveChunkFuture` is the durability API. `saveChunkSync` on the main thread queues
a coupled write and refuses a pending ACK instead of waiting for disk. Off-thread
synchronous callers have a 30-second ACK wait. Shutdown may use the existing
bounded drain on the primary thread. Standalone saves retain their existing API.

Bounds: eight physical chunks per group, 256 groups, 64 MiB of estimated retained serialized
and cleanup work bytes, one maintenance group capture per Level tick, at most two failed
batch resubmissions per maintenance pass, three immediate write attempts and a
30-second retry interval. A bridge exceeding the chunk/group limit requests old
group flushes and retries without a debit. Byte pressure returns no false ACK
and preserves the complete fence; a single image larger than the byte limit
requires raising the configured code bound or reducing that chunk's data. The
limit bounds retained work, not the transient cost of serializing a live chunk.
Autosave counts the number of participant captures rather than one logical save.

The invariant tested here is conservation of one opaque native item across the
registered block-container records after partial save requests and process kill /
fresh LevelDB reopen. This is not a player profile / world, SQL, entity inventory
(moving or parked minecart), world pickup/drop, composter, piston/holder relocation,
plugin direct-write, multiple-provider or historical-data repair guarantee. The
default unsynchronized LevelDB write acknowledges the OS write; power-loss / disk
fsync durability is a separate guarantee.

Native regressions cover both save orders, all public save APIs, provider / Level /
full-chunk unload, lifecycle reentry, close, old reads, physical double-chest
backings, an actual 18-hopper A-B-C chain and over-limit flush/retry. Java and JNI
must each pass the SIGKILL + fresh-process reopen driver before claiming that
backend. Test mocks route Server / Level infrastructure; inventories, holders,
NBT serializers and storage are actual native implementations. These checks do
not establish live client acceptance or TPS improvement. Main snapshot CPU grows
with group size and still needs comparable live p95/p99 measurements.
