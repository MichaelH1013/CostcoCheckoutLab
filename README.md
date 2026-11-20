# Costco Checkout Simulator

CSC 311 Producer-Consumer lab. Customers (producers) shop and then join a checkout queue. Cashiers (consumers) take customers from the queue and serve them. The simulator measures wait times and staff cost so we can decide how many cashiers to schedule.

## Team members and contributions

| Team member | Contribution |
|---|---|
| [Teammate 1 name] | `CheckoutQueue` |
| [Teammate 2 name] | `Customer` |
| [Teammate 3 name] | `Cashier` |
| All three | `CostcoSimulation`, experiments, analysis, README |

## Classes

| Class | What it does |
|---|---|
| `CheckoutQueue` | Wraps an `ArrayBlockingQueue<Integer>` that holds customer IDs. `put` blocks a customer when the line is full and `take` blocks a cashier when the line is empty. It also records wait times, the longest line and how many customers found the line full. |
| `Customer` | A thread that shops for a number of simulated minutes and then joins the queue. |
| `Cashier` | A thread that takes the next customer ID from the queue and serves that customer. Serving takes 2, 3 or 4 minutes depending on the customer ID, so the average is 3 minutes. |
| `CostcoSimulation` | Asks for the parameters, starts the threads, waits for them to finish and prints the results. |

When every customer has joined the line, the main class puts one stop value (-1) in the queue for each cashier. A cashier that takes it goes home, so every customer is served before the cashiers stop.

## How to run

Open the project in IntelliJ, open `CostcoSimulation` and click Run. Or from a terminal in the project folder:

    javac -d out src/costco/*.java
    java -cp out costco.CostcoSimulation

The program asks for four numbers:

1. Number of customers
2. Number of cashiers
3. Queue size (how many customers can wait in line)
4. Minutes over which customers finish shopping

## How time works

One simulated minute is 100 milliseconds of real time. Each customer shops for a random 1 to N minutes, where N is the last number the program asks for, and the random numbers use a fixed seed so every run has the same shoppers. A wait is the time from reaching the checkout to the start of service, so waiting outside a full line counts. Cost is the number of cashiers times $18 per hour times the minutes the store stayed open until the last customer left.

## Experiments

Experiments 1 to 5 use 20 customers who finish shopping over 20 minutes, which is the same 60 customers per hour as the staffing question below.

| # | Test | Cashiers | Queue size | Average wait (min) | Longest wait (min) | Longest line | Found line full | Open (min) | Cost |
|---|---|---|---|---|---|---|---|---|---|
| 1 | Understaffed | 1 | 10 | 22.1 | 39.1 | 10 | 6 | 62.2 | $18.65 |
| 2 | Optimal | 3 | 10 | 0.7 | 3.0 | 3 | 0 | 24.0 | $21.64 |
| 3 | Overstaffed | 10 | 10 | 0.0 | 0.0 | 2 | 0 | 23.0 | $69.09 |
| 4 | Small queue | 3 | 2 | 0.7 | 2.0 | 2 | 1 | 24.1 | $21.65 |
| 5 | Medium queue | 3 | 5 | 0.7 | 3.0 | 3 | 0 | 24.1 | $21.65 |

### Observations

- With one cashier the line filled up and 6 customers had to wait outside it. The average wait was 22.1 minutes and the longest was 39.1 minutes. This run has the lowest cost, but only because it is the cheapest way to serve customers badly.
- Three cashiers cut the average wait to 0.7 minutes and the longest to 3.0 minutes, and the store closed 38 minutes sooner than with one cashier. The cost went up by about $3, not by three times, because the store is open for much less time.
- Ten cashiers gave no waiting at all, but the cost was more than three times higher ($69.09 against $21.64). Most of the cashiers stood idle. The extra cost bought less than one minute of average wait.
- Queue size mattered little when there were enough cashiers. With 3 cashiers, queue sizes of 2, 5 and 10 all gave an average wait of 0.7 minutes. Only the queue of 2 made one customer wait outside the line until a space opened up. A small queue only moves the wait out of the line, it does not remove it.
- These are single runs. Thread timing changes the numbers by about 0.1 minutes from run to run.

## Staffing for 60 customers per hour

These runs use 60 customers who finish shopping over 60 minutes, a queue size of 20 and $18 per hour per cashier.

| Cashiers | Average wait (min) | Longest wait (min) | Longest line | Found line full | Open (min) | Cost |
|---|---|---|---|---|---|---|
| 2 | 17.3 | 31.3 | 20 | 7 | 92.3 | $55.39 |
| 3 | 2.3 | 5.0 | 7 | 0 | 63.1 | $56.82 |
| 4 | 0.3 | 2.0 | 3 | 0 | 61.1 | $73.26 |
| 5 | 0.1 | 1.0 | 2 | 0 | 61.0 | $91.55 |
| 6 | 0.0 | 0.0 | 2 | 0 | 61.0 | $109.87 |
| 8 | 0.0 | 0.0 | 2 | 0 | 61.1 | $146.54 |

### Recommendation

Schedule 4 cashiers, which costs 4 x $18 = $72 per hour.

- Customers arrive at 1 per minute and a cashier needs 3 minutes per customer on average, so each cashier handles about 20 customers per hour. Three cashiers can handle exactly 60 per hour, with no spare room. In the run they still kept the line short, but the longest wait reached 5 minutes. A few slow customers in a row would build a line that never clears.
- The fourth cashier costs $18 more per hour and cuts the average wait from 2.3 minutes to 0.3 minutes and the longest wait from 5.0 minutes to 2.0 minutes.
- The fifth cashier costs another $18 per hour and saves only 0.2 minutes of average wait, so it is not worth it. Six or more cashiers give no waiting at all but cost at least $108 per hour.
- Two cashiers cannot keep up. The line filled to 20, 7 customers waited outside it, the average wait was 17.3 minutes and the store stayed open 32 minutes past the hour.

---

# Extra credit: Stadium Entry Simulator

The same Producer-Consumer idea, rebuilt as a scalable stadium simulation for sporting events and concerts. Fans (producers) arrive, walk to an entry gate and join a line. Gate scanners (consumers) take the next fan from the line and let the fan in. The Costco code in `src/costco` is unchanged. The new code is in `src/stadium`.

| Costco lab | Stadium version |
|---|---|
| `Customer` thread | `Fan`, a small object. Producer threads create fans, so there is no thread per fan. |
| `Cashier` thread | `GateScanner` thread, several at each `Gate` |
| `CheckoutQueue` | `FanLine`: an `ArrayBlockingQueue` lane plus a list of fans waiting outside it |
| `CostcoSimulation` | `StadiumSimulation` (the engine), `StadiumApp` (the GUI) and `StadiumConsole` (no GUI) |
| stop value `-1` | `Fan.STOP`, one per scanner, put behind the last fan |

## How to run

The GUI uses Swing, which comes with the JDK, so there is nothing extra to install. It needs Java 17 or newer and was built on JDK 25.

In IntelliJ, open `StadiumApp` and click Run. (To switch between the stadium and the parking lots in one window, run `app.SimulatorApp` instead. It is described in the parking section below.) Or from a terminal in the project folder:

    javac -d out src/costco/*.java src/stadium/*.java
    java -cp out stadium.StadiumApp

To run without the GUI and print the results, like `CostcoSimulation`:

    java -cp out stadium.StadiumConsole football gates=12 scanners=3 strategy=shortest_line

`StadiumConsole` takes a preset (`demo`, `football`, `concert` or `stress`) and any number of `name=value` settings from the table below. With no arguments it runs the small demo.

## What the GUI shows

- A top-down stadium with the gates drawn around the edge. Each gate has scanner booths (green when busy) and a lane running outward from it.
- Fans are dots. Gray fans walk to a gate, blue fans stand in the lane, green fans are being scanned and gold fans walk in to a seat. Orange fans arrived when the lane was full and wait outside it.
- With the shared line strategy the line is drawn in a holding area under the stadium and fans are called from it to whichever scanner is free.
- Live numbers on the right: simulated time, fans walking, in lanes, waiting outside, being scanned and inside, average and longest wait, longest line, fans who found the lane full, admitted per minute and scanner cost so far. A chart shows how many fans are waiting over time, and a table shows each gate's lane, busy scanners, fans admitted, average wait and fans admitted per minute.
- Controls on the left: a preset, every parameter below, a speed slider, and Start, Pause and Reset. Speed can be changed while the simulation runs. The other numbers can be changed before Start or after Reset.

## Parameters

Changing the size of the event only means changing these numbers. No code changes.

| Setting (console name) | Default (football) | What it does |
|---|---|---|
| Gates (`gates`) | 8 | Entry points around the stadium, 1 to 200 |
| Scanners per gate (`scanners`) | 2 | Scanner threads at each gate |
| Lane capacity (`queue`) | 40 | Fans that fit in one lane. A shared line holds this times the number of gates. |
| Scan time (`service`) | 7 seconds | Average time to scan a fan. Each fan takes 0.5 to 1.5 times this. |
| Fans (`fans`) | 4,000 | Fans who arrive in total, 1 to 1,000,000 |
| Arrival window (`minutes`) | 60 | Simulated minutes over which the fans arrive |
| Arrival pattern (`shape`) | `surge` | `uniform` is a steady stream, `surge` bunches fans up before the start, `late_rush` bunches them at the end |
| Walk time (`walk`) | 45 seconds | How long a fan walks from the approach to the line |
| Line strategy (`strategy`) | `assigned_gate` | `assigned_gate`: each fan goes to a gate picked at random. `shortest_line`: each fan picks the gate with the fewest people. `shared_queue`: one line feeds every scanner. |
| Gate bias (`bias`) | 1 | 0 means all gates are equally popular. 1 means gate 1 draws twice as many fans as the last gate. Used by `assigned_gate`. |
| Producer threads (`producers`) | 2 | Threads that create fans and walk them to the gates |
| Speed (`speed`) | 120 | Simulated seconds per real second |
| Random seed (`seed`) | 311 | Console only |

Presets only fill in these numbers. The football game has 4,000 fans and 8 gates. The concert has 3,000 fans, 6 gates, slower bag checks (10 seconds) and a late rush. The stress test has 50,000 fans, 40 gates and the shortest line strategy.

## How it scales

- **A fan is an object, not a thread.** In the Costco code each customer was a thread that slept and then blocked in `put` when the line was full. That works for 20 customers but 50,000 blocked threads would not. Here a small `ScheduledThreadPoolExecutor` of producer threads wakes up every few milliseconds, creates the fans who are due and moves fans who finished walking into the line.
- **Scanners are a fixed thread pool**, one thread per scanner (gates times scanners per gate). Each scanner blocks on `take()` when its lane is empty, exactly like the cashiers. The number of threads depends only on the staffing numbers, never on the number of fans.
- **A full lane does not block anyone.** A fan who finds the lane full is parked in an outside list, in arrival order, and moved into the lane when a scanner takes a fan out. This keeps the bounded buffer and the "found the lane full" count from the Costco version without a waiting thread per fan.
- **Waiting is measured the same way.** A wait is the time from reaching the line to the start of scanning, so waiting outside a full lane counts.
- **The picture draws a limited number of dots.** The simulation holds every fan, but each lane in the GUI only draws as many dots as fit. The stadium keeps only the last 1,024 admitted fans for the walk to the seat, so memory stays flat no matter how many fans get in.

## Thread safety

- `FanLine.join` and `FanLine.promoteOutsideFans` are `synchronized` because the lane and the outside list must change together. A new fan only goes straight into the lane when nobody is waiting outside, so the line stays first come, first served.
- The lane is an `ArrayBlockingQueue`, which does its own locking. Fans arriving in a gate's approach are held in a `PriorityBlockingQueue` ordered by arrival time.
- Counters that many scanners update (fans admitted, total wait, found the lane full) use `LongAdder` and `AtomicLong`, so scanner threads do not wait for one lock.
- A fan's state fields are `volatile`, because scanner threads write them and the GUI reads them.
- `StadiumConfig` is an immutable record, so every thread can read it safely.
- The simulation clock can be paused and its speed changed while threads read it, so it keeps its state in one immutable object behind a `volatile` field. Reading the time takes no lock.
- The simulation threads never touch Swing. A Swing timer on the event thread reads the simulation's counters and queues about 30 times a second.

## Experiments

These are single runs from `StadiumConsole` at speed 600, so thread timing moves the numbers a little between runs.

**Line strategy.** Football game: 4,000 fans, 8 gates, 2 scanners per gate, lane capacity 40, and gate 1 draws twice as many fans as gate 8.

| Strategy | Average wait (min) | Longest wait (min) | Longest line | Found lane full |
|---|---|---|---|---|
| `assigned_gate` | 0.7 | 5.7 | 88 | 398 |
| `shortest_line` | 0.0 | 0.3 | 4 | 0 |
| `shared_queue` | 0.0 | 0.1 | 9 | 0 |

The scanners are the same in every row, so the cost is the same ($292 for 16 scanners). When fans go to an assigned gate the popular gates build long lines while other gates have idle scanners. Letting fans pick the shortest line, or sharing one line, spreads the work evenly and removes the waiting.

**Scale.** The same code with only the parameters changed. Fans arrive over 60 minutes (10 for the demo).

| Fans | Gates x scanners | Scanner threads | Producer threads | Average wait (min) | Longest wait (min) | Real time (s) |
|---|---|---|---|---|---|---|
| 150 | 3 x 2 | 6 | 1 | 0.1 | 0.3 | 1.1 |
| 4,000 | 8 x 2 | 16 | 2 | 0.7 | 5.7 | 6.1 |
| 50,000 | 40 x 3 | 120 | 4 | 2.4 | 6.0 | 6.3 |
| 200,000 | 100 x 4 | 400 | 8 | 5.6 | 13.2 | 7.1 |

The real time barely grows because one simulated hour takes about 6 real seconds at this speed, however many fans there are. Going from 150 to 200,000 fans took no code changes and no extra thread per fan.

---

# Extra credit: Stadium Parking Simulator

The stadium simulation above covers getting into the stadium. This one covers getting a car into a parking lot around it, and the two share one window. It is the same Producer-Consumer idea with one new piece: the lot's spaces are a second bounded resource. Cars (producers) drive to a lot and join the line at its entrance. Attendants (consumers) take the next car, take the payment and wave it into a space. When the lot is full, the attendant turns the car away. The code is in `src/parking`, and the window with both simulations is in `src/app`.

| Stadium | Parking |
|---|---|
| `Fan`, a small object | `Car`, a small object |
| `FanLine` | `CarLine`: the lane at a lot entrance plus the cars backed up onto the street |
| `Gate` | `Lot`, which also owns its parking spaces |
| `GateScanner` thread | `Attendant` thread |
| `Fan.STOP` | `Car.STOP` |
| `StadiumSimulation`, `StadiumPanel`, `StadiumView` | `ParkingSimulation`, `ParkingPanel`, `ParkingView` |
| `StadiumApp`, `StadiumConsole` | `ParkingApp`, `ParkingConsole` |

The parking code also reuses `SimClock`, `ArrivalShape` and `HistoryChart` from the stadium package.

## How to run

In IntelliJ, open `app.SimulatorApp` and click Run. It opens one window with three tabs: "Stadium entry", "Parking lots" and "Whole event" (the last one links the other two, see the whole event section below). Click a tab to switch. Each tab has its own simulation with its own controls, so one can keep running while you look at another. `parking.ParkingApp` opens only the parking tab, and `stadium.StadiumApp` opens only the stadium tab.

From a terminal in the project folder:

    javac -d out src/costco/*.java src/stadium/*.java src/parking/*.java src/app/*.java
    java -cp out app.SimulatorApp

To run the parking lots without the GUI and print the results:

    java -cp out parking.ParkingConsole football lots=8 strategy=most_free_spaces

`ParkingConsole` takes a preset (`demo`, `football`, `concert` or `stress`) and any number of `name=value` settings from the table below. With no arguments it runs the small demo.

## What the parking GUI shows

- Two layouts, chosen with the Lot layout setting. They only change the picture, not the simulation.
  - **Lots all around the stadium** looks like MetLife Stadium. The stadium is in the middle with a plaza and a ring road around it. One big surface lot fills the ground around it and is split into lots A, B, C and so on, each with its own entrance on the outer edge.
  - **One big lot behind the venue** looks like Jones Beach Theater. The seating fans out from a stage on the water and one big lot lies behind it, split into side by side strips. Lot A is the strip in the middle and the lots go outward from it. Cars come in from the highway along the top.
- Cars arrive from the highway at the edge of the picture and wait on an access road to the lot entrance. There are payment booths at the entrance (green when an attendant has a car). The barrier turns red when the lot is full.
- Cars are small squares. Gray cars drive to a lot, blue cars wait in the entrance lane, orange cars have backed up onto the street because the lane is full, green cars are at a payment booth, and teal squares are parked cars. A red car was turned away.
- Each lot is a grid of spaces that fills up starting next to the venue, because that is where people park first. A lot with few spaces has one big square per space. A lot with thousands of spaces cannot draw one square per space, so one square stands for several.
- Gold dots are fans walking from their space to the stadium or the seating. Each lot shows its walk time, which gets longer for the lots farther down the alphabet.
- Live numbers on the right: cars driving, in lanes, on the street, at a booth, parked and turned away, free spaces left, average and longest wait, longest line, parking fees earned and lost, and attendant cost so far. A chart shows how many cars are waiting over time, and a table shows each lot.
- The controls are the same as the stadium's: a preset, every parameter below, a speed slider, and Start, Pause and Reset.

## Parking parameters

| Setting (console name) | Default (football) | What it does |
|---|---|---|
| Lots (`lots`) | 6 | Parking lots around the stadium, 1 to 100 |
| Spaces per lot (`spaces`) | 300 | Spaces in each lot, up to 100,000 |
| Attendants per lot (`attendants`) | 2 | Attendant threads at each lot entrance |
| Lane capacity (`queue`) | 25 | Cars that fit in one entrance lane. Extra cars wait on the street. |
| Pay time (`pay`) | 15 seconds | Average time to take payment or check a pass. Each car takes 0.5 to 1.5 times this. |
| Cars (`cars`) | 1,700 | Cars that arrive in total, 1 to 1,000,000 |
| Arrival window (`minutes`) | 90 | Simulated minutes over which the cars arrive |
| Arrival pattern (`shape`) | `surge` | `uniform`, `surge` or `late_rush`, the same as the stadium |
| Drive time (`drive`) | 40 seconds | How long a car drives from the main road to the back of the line |
| Walk time (`walk`) | 120 seconds | Walk from the nearest lot to the stadium. The farthest lot is 3 times as far. |
| Lot strategy (`strategy`) | `assigned_lot` | `assigned_lot`: each car picks a lot at random. `shortest_line`: each car picks the entrance with the fewest cars. `most_free_spaces`: each car picks the lot with the most room. The last two skip full lots. |
| Lot bias (`bias`) | 1 | 0 means all lots are equally popular. 1 means the nearest lot draws twice as many cars as the farthest. Used by `assigned_lot`. |
| Producer threads (`producers`) | 2 | Threads that create cars and drive them to the lots |
| Speed (`speed`) | 120 | Simulated seconds per real second |
| Lot layout (`layout`) | `surrounding` | `surrounding` draws the lots all around the stadium like MetLife. `behind_venue` draws one big lot behind the venue like Jones Beach Theater. Only changes the picture. |
| Random seed (`seed`) | 311 | Console only |

The presets are `demo` (150 cars and 3 lots of 40 spaces, so the lots fill up), `football` (1,700 cars and 6 lots all around the stadium), `concert` (1,000 cars, 4 lots behind the venue, slow cash payments and a late rush) and `stress` (50,000 cars and 30 lots of 2,000 spaces). The parking fee is $25 per car and an attendant costs $18 per hour.

## How the parking simulation scales and stays thread-safe

- The thread design is the same as the stadium's. A car is an object, not a thread. A small `ScheduledThreadPoolExecutor` of producer threads creates the cars that are due and moves cars that finished driving into the lot's line. A fixed thread pool runs one attendant thread per attendant (lots times attendants per lot), and each attendant blocks on `take()` when its lane is empty. A full lane parks cars in an outside list instead of blocking a thread, so 150 cars and 200,000 cars use the same kinds of threads.
- The spaces in a lot are a `Semaphore`. An attendant takes a space with `tryAcquire()` before taking the payment, so many attendants working at once can never hand out more spaces than the lot has. If `tryAcquire()` fails, the car is turned away. The test run for this checks that no lot ever has more parked cars than spaces and that a lot only turns cars away when it is full.
- `CarLine.join` and `CarLine.promoteOutsideCars` are `synchronized` for the same reason as in `FanLine`. Counters use `LongAdder` and `AtomicLong`, a car's state fields are `volatile`, and the settings are an immutable record.
- Keep the speed at about 1,000 or lower for experiments. Above that, one payment lasts only a millisecond or two of real time, the operating system's sleep timing is no longer accurate enough, and the waits come out too long.

## Parking experiments

These are single runs from `ParkingConsole` at speed 600, so thread timing moves the numbers a little between runs.

**Lot strategy.** Football game: 1,700 cars, 6 lots of 300 spaces (1,800 in total), 2 attendants per lot, and the nearest lot draws twice as many cars as the farthest.

| Strategy | Cars parked | Turned away | Average wait (min) | Longest wait (min) | Longest line | Found lane full | Average walk (min) | Parking fees |
|---|---|---|---|---|---|---|---|---|
| `assigned_lot` | 1,521 | 179 | 1.3 | 8.2 | 63 | 201 | 3.8 | $38,025 |
| `shortest_line` | 1,700 | 0 | 0.2 | 0.7 | 4 | 0 | 4.0 | $42,500 |
| `most_free_spaces` | 1,700 | 0 | 0.1 | 0.6 | 4 | 0 | 4.0 | $42,500 |

There was room for every car, but with `assigned_lot` the nearest lots filled up while the farthest ones stayed half empty. The full lots turned away 179 cars, which is $4,475 in lost fees, and made the others wait. The two smarter strategies parked everyone with almost no waiting. The cost is a slightly longer average walk, because some cars use the farther lots.

**Scale.** The same code with only the parameters changed. Cars arrive over 90 minutes (10 for the demo).

| Cars | Lots x attendants | Attendant threads | Producer threads | Turned away | Average wait (min) | Longest wait (min) | Real time (s) |
|---|---|---|---|---|---|---|---|
| 150 | 3 x 1 | 3 | 1 | 38 | 1.3 | 3.4 | 1.1 |
| 1,700 | 6 x 2 | 12 | 2 | 179 | 1.3 | 8.2 | 9.1 |
| 50,000 | 30 x 6 | 180 | 4 | 0 | 1.0 | 3.3 | 9.1 |
| 200,000 | 100 x 6 | 600 | 8 | 0 | 11.2 | 26.4 | 11.3 |

The demo turns cars away because it only has 120 spaces for 150 cars, and the 1,700 car row uses `assigned_lot`. The other two rows use `most_free_spaces`. The 200,000 car run has waits of 11 minutes because 600 attendants cannot keep up with the peak of the surge, which is what you would expect from that staffing.

---

# Extra credit: The whole event (parking, then the gates)

The stadium and the parking lots also run together. Cars arrive and park, and the fans in each car walk from their lot to the stadium and join the line at a gate. The two halves now feed each other: a slow lot entrance starves the gates, and a slow gate backs the fans up. The code is in `src/event`.

## How to run

In IntelliJ, open `app.SimulatorApp` and click the third tab, "Whole event". The left of the window has the controls for both halves, the middle shows the parking lots and the stadium gates side by side, and the right has the numbers for the whole event. Start, Pause, Resume, Reset and the speed slider act on both halves at once.

To run it without a window and print the results:

    java -cp out event.EventConsole football lots=8 gates=12 fanspercar=4

`EventConsole` takes a preset (`demo`, `football`, `concert` or `stress`) and any of these settings: `cars`, `lots`, `spaces`, `attendants`, `pay`, `minutes`, `lotstrategy`, `gates`, `scanners`, `scan`, `gatestrategy`, `fanspercar` and `speed`. The meaning of each is in the parameter tables above.

## What is new

| Setting | Default (football) | What it does |
|---|---|---|
| Fans per car (`fanspercar`) | 3 | Each car that parks lets this many fans out. They walk to the stadium together. A car that is turned away brings no fans, so the stadium has fewer fans than cars times fans per car. |

The walk from the lot to the stadium is the lot's walk time, which is longer for the lots farther down the alphabet. After that the fans walk from the stadium approach to their gate line, which is the stadium's own walk time. The football preset is the football parking and the football stadium joined with 3 fans per car, and the concert preset joins the two concert settings the same way.

## How the two simulations are linked

- Both simulations run on **one shared `SimClock`**, so pausing and changing the speed affect the whole event. Neither half pauses the shared clock when it finishes. The event does that when the last fan is inside.
- Each time an attendant parks a car, a listener on the parking simulation puts the car's fans in a `PriorityBlockingQueue`, ordered by the time they reach the stadium (the time the car parked plus the walk from its lot).
- A single **bridge thread** watches the clock. When a group's time comes, it hands the fans to the stadium with `injectFans`. The stadium picks a gate for each fan with its normal gate strategy. Its own producer threads then walk the fans to the lines and the scanners take them, exactly as before.
- When the parking is finished and the last group has been handed over, the bridge calls `closeArrivals`. The stadium then sends its stop values and finishes once the last fan is inside.
- The stadium did not need a new thread design. It only gained a mode where the fans come from outside instead of being created on a schedule. The stadium on its own still works the same way as before.
- All fans and cars are still objects, not threads. The whole event uses the parking attendant threads, the stadium scanner threads, a few producer threads in each half and the one bridge thread.

## Experiments

These are single runs from `EventConsole` at speed 600, so thread timing moves the numbers a little between runs.

| Event | Gates x scanners | Parking average wait (min) | Gate average wait (min) | Gate longest wait (min) | Last fan inside (min) |
|---|---|---|---|---|---|
| Football, 1,521 cars parked, 4,563 fans | 8 x 2 | 1.2 | 2.1 | 13.4 | 100.2 |
| Football, same cars | 12 x 2 | 1.2 | 0.1 | 1.4 | 97.4 |
| Concert, 911 cars parked, 2,733 fans | 6 x 3 | 11.2 | 0.2 | 1.0 | 79.9 |
| Stress, 50,000 cars parked, 100,000 fans | 40 x 3 | 5.8 | 21.1 | 45.1 | 153.1 |

**The bottleneck moves.** At the football game the gates are the problem: 8 gates with 2 scanners gave an average wait of 2.1 minutes, and 12 gates removed it, with the last fan inside 3 minutes sooner. At the concert the parking lots are the problem: the cash payments at 8 attendants gave an 11 minute average wait there, while the gates had almost none. The same concert fans, all arriving at the gates at once with no parking in front, gave a 3.1 minute average wait at the gates in the stadium on its own. Parking spreads the crowd out, so the gates see a steadier stream. It also means that adding more scanners to the concert would do nothing, while adding attendants would help.

The stress test is the largest run: 50,000 cars and 100,000 fans took about 15 seconds of real time with 180 attendant threads, 120 scanner threads and a few producer threads. Here the gates were the bottleneck, with 21 minute waits.
