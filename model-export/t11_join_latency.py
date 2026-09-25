#!/usr/bin/env python3
"""
iTantra T11 — cross-device latency join

Joins phone A's (sender) and phone B's (receiver) telemetry.csv pulls by
(sender_id, sequence) and computes, per matched message:

    delta_ms = first_audio_epoch_ms(B) - peer_offset_ms(B) - speech_end_epoch_ms(A)

first_audio_epoch_ms(B) is on B's wall clock; peer_offset_ms(B) is B's own
Telemetry.peerClockOffsetMs at receive time, i.e. offset(B - A) as derived by the
NTP-style ping/ACK exchange in SocketTransport.kt (T11). Subtracting it expresses
B's epoch reading on A's clock, so the difference against speech_end_epoch_ms(A)
(also on A's clock) is a same-clock delta — the PS-26173 headline number: the time
from the sentence being said on A to the same sentence starting as audio on B.

Usage:
    python t11_join_latency.py --sender sender_telemetry.csv \\
                                --receiver receiver_telemetry.csv \\
                                --out joined.csv
"""

import argparse
import csv
import statistics
from pathlib import Path
from typing import Dict, List, Tuple

JOINED_FIELDS = [
    "sender_id", "sequence", "speech_end_epoch_ms", "first_audio_epoch_ms",
    "peer_offset_ms", "peer_rtt_ms", "delta_ms",
]


def read_rows(path: str) -> List[Dict[str, str]]:
    with open(path, newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f))


def sender_index(rows: List[Dict[str, str]]) -> Dict[Tuple[str, str], Dict[str, str]]:
    index = {}
    for row in rows:
        if int(row.get("speech_end_epoch_ms", "0") or 0) == 0:
            continue
        index[(row["sender_id"], row["sequence"])] = row
    return index


def join(sender_rows: List[Dict[str, str]], receiver_rows: List[Dict[str, str]]) -> List[Dict[str, object]]:
    by_key = sender_index(sender_rows)
    joined = []
    for row in receiver_rows:
        first_audio = int(row.get("first_audio_epoch_ms", "0") or 0)
        if first_audio == 0:
            continue
        key = (row["sender_id"], row["sequence"])
        sender_row = by_key.get(key)
        if sender_row is None:
            continue
        speech_end = int(sender_row["speech_end_epoch_ms"])
        offset = int(row.get("peer_offset_ms", "0") or 0)
        rtt = int(row.get("peer_rtt_ms", "0") or 0)
        delta_ms = first_audio - offset - speech_end
        joined.append({
            "sender_id": row["sender_id"],
            "sequence": row["sequence"],
            "speech_end_epoch_ms": speech_end,
            "first_audio_epoch_ms": first_audio,
            "peer_offset_ms": offset,
            "peer_rtt_ms": rtt,
            "delta_ms": delta_ms,
        })
    return joined


def write_joined(path: str, rows: List[Dict[str, object]]) -> None:
    with open(path, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=JOINED_FIELDS)
        writer.writeheader()
        writer.writerows(rows)


def report(rows: List[Dict[str, object]]) -> None:
    print(f"Matched {len(rows)} message(s) by (sender_id, sequence).")
    if not rows:
        return
    deltas = [r["delta_ms"] for r in rows]
    print(f"  median delta_ms: {statistics.median(deltas):.1f}")
    print(f"  mean delta_ms:   {statistics.mean(deltas):.1f}")
    print(f"  min delta_ms:    {min(deltas)}")
    print(f"  max delta_ms:    {max(deltas)}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--sender", required=True, help="Phone A's telemetry.csv (send-side rows)")
    parser.add_argument("--receiver", required=True, help="Phone B's telemetry.csv (receive-side rows)")
    parser.add_argument("--out", required=True, help="Path to write the joined CSV")
    args = parser.parse_args()

    sender_rows = read_rows(args.sender)
    receiver_rows = read_rows(args.receiver)
    joined = join(sender_rows, receiver_rows)

    Path(args.out).parent.mkdir(parents=True, exist_ok=True)
    write_joined(args.out, joined)
    report(joined)


if __name__ == "__main__":
    main()
