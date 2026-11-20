# Costco Checkout Simulator

CSC 311 Producer-Consumer lab. Customers (producers) shop and then join a checkout queue. Cashiers (consumers) take customers from the queue and serve them. The simulator measures wait times and staff cost so we can decide how many cashiers to schedule.

## Team members and contributions

| Team member | Contribution |
|---|---|
| [Teammate 1 name] | `CheckoutQueue` |
| [Teammate 2 name] | `Customer` |
| [Teammate 3 name] | `Cashier` |
| All three | `CostcoSimulation`, experiments, analysis, README |

## Use of AI

AI assistance was used to improve this project, as the professor invited. The tool was Claude (made by Anthropic), used through Claude Code. This notice covers the extra credit work.

- **Where it was used:** the extra credit. It wrote most of the code and text for the stadium, parking and whole event simulations and their windows (`src/stadium`, `src/parking`, `src/event`, `src/app`), the code they share (`src/common`), the tests and experiments (`test`, `src/experiments`) and the extra credit sections of this README. It also ran the tests and the experiments, and the numbers in those sections come from those runs. The Costco lab in `src/costco` was not changed during the extra credit work.
- **How it was checked:** by the automatic tests, a comparison with queueing theory and repeated runs, described under "Checking that the results can be trusted". The repeated runs found a real mistake in the first set of experiments, which had been run at too high a speed. It was corrected, and the section "Repeated runs, and a mistake they found" explains what went wrong.

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
| `CheckoutQueue` | `FanLine`, a thin subclass of the generic `ArrivalLine` in `src/common`: an `ArrayBlockingQueue` lane plus a list of fans waiting outside it |
| `CostcoSimulation` | `StadiumSimulation` (the engine), `StadiumApp` (the GUI) and `StadiumConsole` (no GUI) |
| stop value `-1` | `Fan.STOP`, one per scanner, put behind the last fan |

## How to run

The GUI uses Swing, which comes with the JDK, so there is nothing extra to install. It needs Java 21 or newer (the thread comparison in `src/experiments` uses virtual threads) and was built on JDK 25.

In IntelliJ, open `StadiumApp` and click Run. (To switch between the stadium and the parking lots in one window, run `app.SimulatorApp` instead. It is described in the parking section below.) Or from a terminal in the project folder:

    javac -d out src/*/*.java
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
| Crowd limit (`crowd`) | 0 (no limit) | Most fans allowed to wait outside a full lane at each gate. A fan who arrives when it is full gives up and is counted. A shared line allows this times the number of gates. |
| Random seed (`seed`) | 311 | Console only |

Presets only fill in these numbers. The football game has 4,000 fans and 8 gates. The concert has 3,000 fans, 6 gates, slower bag checks (10 seconds) and a late rush. The stress test has 50,000 fans, 40 gates and the shortest line strategy.

## How it scales

- **A fan is an object, not a thread.** In the Costco code each customer was a thread that slept and then blocked in `put` when the line was full. That works for 20 customers but 50,000 blocked threads would not. Here a small `ScheduledThreadPoolExecutor` of producer threads wakes up every few milliseconds, creates the fans who are due and moves fans who finished walking into the line.
- **Scanners are a fixed thread pool**, one thread per scanner (gates times scanners per gate). Each scanner blocks on `take()` when its lane is empty, exactly like the cashiers. The number of threads depends only on the staffing numbers, never on the number of fans.
- **A full lane does not block anyone, and the crowd outside it can be limited.** In the Costco lab a customer thread blocked in `put` when the line was full, which is the classic bounded buffer where the producer waits. Here a fan who finds the lane full is parked in an outside list, in arrival order, and moved into the lane when a scanner makes room. This keeps the `ArrayBlockingQueue` lane and the "found the lane full" count without a waiting thread per fan (the thread comparison below shows what one thread per fan costs). The trade-off is that the outside list is not bounded, so memory is not bounded either and nothing slows the producers down. A real crowd behaves like that, since people keep arriving however long the line is, but a real crowd also gives up. The crowd limit setting models that: when the lane and the crowd outside it are both full, a new fan gives up, is counted and is not queued. With the limit at 0 nobody ever gives up.
- **Waiting is measured the same way.** A wait is the time from reaching the line to the start of scanning, so waiting outside a full lane counts.
- **The picture draws a limited number of dots.** The simulation holds every fan, but each lane in the GUI only draws as many dots as fit. The stadium keeps only the last 1,024 admitted fans for the walk to the seat, so memory stays flat no matter how many fans get in.

## Thread safety

- `ArrivalLine.join` and `ArrivalLine.promoteOutside` are `synchronized` because the lane and the outside list must change together. `ArrivalLine` is the one generic class behind both `FanLine` and `CarLine`. A new fan only goes straight into the lane when nobody is waiting outside, so the line stays first come, first served. If `synchronized` is removed from those two methods, the stress test in the Tests section fails every time.
- The lane is an `ArrayBlockingQueue`, which does its own locking. Fans arriving in a gate's approach are held in a `PriorityBlockingQueue` ordered by arrival time.
- Counters that many scanners update (fans admitted, total wait, found the lane full) use `LongAdder` and `AtomicLong`, so scanner threads do not wait for one lock.
- A fan's state fields are `volatile`, because scanner threads write them and the GUI reads them.
- `StadiumConfig` is an immutable record, so every thread can read it safely.
- The simulation clock can be paused and its speed changed while threads read it, so it keeps its state in one immutable object behind a `volatile` field. Reading the time takes no lock.
- The simulation threads never touch Swing. A Swing timer on the event thread reads the simulation's counters and queues about 30 times a second.

## Experiments

Each row is the mean ± standard deviation over 10 runs of `experiments.Replications`, with a different random seed in each run, at a speed of 120 so that a scan lasts about 60 ms of real time. One run is not enough to compare two settings, because real threads make every run a little different, and the standard deviation shows how large that difference is. The section "Repeated runs" below explains how to run this and why the speed is 120.

**Line strategy.** Football game: 4,000 fans, 8 gates, 2 scanners per gate, lane capacity 40, and gate 1 draws twice as many fans as gate 8.

| Strategy | Average wait (min) | Longest wait (min) | Longest line | Found lane full |
|---|---|---|---|---|
| `assigned_gate` | 0.7 ± 0.3 | 5.4 ± 1.8 | 84 ± 24 | 372 ± 159 |
| `shortest_line` | 0.1 ± 0.0 | 0.3 ± 0.0 | 5 ± 1 | 0 ± 0 |
| `shared_queue` | 0.0 ± 0.0 | 0.1 ± 0.0 | 9 ± 2 | 0 ± 0 |

The differences between the rows are many times larger than the spread inside a row, so the ranking is real. The assigned gate row is less steady than the others because gate 1 is close to overloaded at the peak, and a line near its limit is very sensitive to small changes (an earlier run of this experiment gave 0.9 ± 0.1 for the average wait, see "Repeated runs"). The scanners are the same in every row, so the cost is the same (about $292 for 16 scanners). When fans go to an assigned gate the popular gates build lines of about 100 fans while other gates have idle scanners. Letting fans pick the shortest line, or sharing one line, spreads the work evenly and almost removes the waiting.

**Crowd limit.** The same football game with assigned gates, changing how many fans may wait outside a full lane at each gate. A fan who arrives when the lane and the crowd are both full gives up.

| Crowd limit per gate | Fans who gave up | Average wait (min) | Longest wait (min) | Longest line |
|---|---|---|---|---|
| none | 0 ± 0 | 0.9 ± 0.1 | 6.7 ± 0.9 | 101 ± 12 |
| 50 | 10 ± 12 | 0.8 ± 0.2 | 5.9 ± 0.6 | 86 ± 7 |
| 20 | 38 ± 18 | 0.7 ± 0.1 | 4.2 ± 0.1 | 60 ± 0 |
| 10 | 33 ± 21 | 0.5 ± 0.1 | 3.4 ± 0.3 | 50 ± 0 |

A limit caps the line at the lane plus the limit (40 + 20 = 60 for a limit of 20), so the longest line and the longest wait fall, and the price is the fans who give up. The number who give up changes a lot from run to run, so with 10 runs the rows for 20 and 10 cannot be told apart in that column.

**Scale.** The same code with only the parameters changed. Fans arrive over 60 minutes (10 for the demo). These are single runs at a speed of 600, where one simulated hour takes about 6 real seconds. The waits are left out of this table on purpose: at that speed a scan lasts only about 12 ms and the waits are inflated by the operating system's timer (see "Repeated runs"), so this table is only about threads and time.

| Fans | Gates x scanners | Scanner threads | Producer threads | Real time (s) |
|---|---|---|---|---|
| 150 | 3 x 2 | 6 | 1 | 1.1 |
| 4,000 | 8 x 2 | 16 | 2 | 6.1 |
| 50,000 | 40 x 3 | 120 | 4 | 6.3 |
| 200,000 | 100 x 4 | 400 | 8 | 7.1 |

The real time barely grows because one simulated hour takes about 6 real seconds at this speed, however many fans there are. Going from 150 to 200,000 fans took no code changes and no extra thread per fan. The thread comparison below shows how this compares with one thread per fan.

---

# Extra credit: Stadium Parking Simulator

The stadium simulation above covers getting into the stadium. This one covers getting a car into a parking lot around it, and the two share one window. It is the same Producer-Consumer idea with one new piece: the lot's spaces are a second bounded resource. Cars (producers) drive to a lot and join the line at its entrance. Attendants (consumers) take the next car, take the payment and wave it into a space. When the lot is full, the attendant turns the car away. The code is in `src/parking`, and the window with both simulations is in `src/app`.

| Stadium | Parking |
|---|---|
| `Fan`, a small object | `Car`, a small object |
| `FanLine` | `CarLine`, the second thin subclass of `ArrivalLine`: the lane at a lot entrance plus the cars backed up onto the street |
| `Gate` | `Lot`, which also owns its parking spaces |
| `GateScanner` thread | `Attendant` thread |
| `Fan.STOP` | `Car.STOP` |
| `StadiumSimulation`, `StadiumPanel`, `StadiumView` | `ParkingSimulation`, `ParkingPanel`, `ParkingView` |
| `StadiumApp`, `StadiumConsole` | `ParkingApp`, `ParkingConsole` |

The stadium, parking and event code share `ArrivalLine`, `SimClock`, `ArrivalShape`, `HistoryChart` and the form helpers in `src/common`.

## How to run

In IntelliJ, open `app.SimulatorApp` and click Run. It opens one window with three tabs: "Stadium entry", "Parking lots" and "Whole event" (the last one links the other two, see the whole event section below). Click a tab to switch. Each tab has its own simulation with its own controls, so one can keep running while you look at another. `parking.ParkingApp` opens only the parking tab, and `stadium.StadiumApp` opens only the stadium tab.

From a terminal in the project folder:

    javac -d out src/*/*.java
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
| Street limit (`street`) | 0 (no limit) | Most cars allowed to wait on the street behind a full entrance lane at each lot. A car that arrives when it is full drives on and is counted. |
| Lot layout (`layout`) | `surrounding` | `surrounding` draws the lots all around the stadium like MetLife. `behind_venue` draws one big lot behind the venue like Jones Beach Theater. Only changes the picture. |
| Random seed (`seed`) | 311 | Console only |

The presets are `demo` (150 cars and 3 lots of 40 spaces, so the lots fill up), `football` (1,700 cars and 6 lots all around the stadium), `concert` (1,000 cars, 4 lots behind the venue, slow cash payments and a late rush) and `stress` (50,000 cars and 30 lots of 2,000 spaces). The parking fee is $25 per car and an attendant costs $18 per hour.

## How the parking simulation scales and stays thread-safe

- The thread design is the same as the stadium's. A car is an object, not a thread. A small `ScheduledThreadPoolExecutor` of producer threads creates the cars that are due and moves cars that finished driving into the lot's line. A fixed thread pool runs one attendant thread per attendant (lots times attendants per lot), and each attendant blocks on `take()` when its lane is empty. A full lane parks cars in an outside list instead of blocking a thread, so 150 cars and 200,000 cars use the same kinds of threads.
- The spaces in a lot are a `Semaphore`. An attendant takes a space with `tryAcquire()` before taking the payment, so many attendants working at once can never hand out more spaces than the lot has. If `tryAcquire()` fails, the car is turned away. The test run for this checks that no lot ever has more parked cars than spaces and that a lot only turns cars away when it is full.
- The `synchronized` join and promote are in the shared `ArrivalLine`, for the same reason as in the stadium. Counters use `LongAdder` and `AtomicLong`, a car's state fields are `volatile`, and the settings are an immutable record.
- Use a speed of about 120 or lower for experiments. At 600 a payment lasts only about 25 ms of real time, the operating system's timer is not accurate enough, and the waits come out too long (see "Repeated runs").

## Parking experiments

Each row is the mean ± standard deviation over 10 runs of `experiments.Replications`, with a different random seed in each run, at a speed of 120 (see "Repeated runs" below).

**Lot strategy.** Football game: 1,700 cars, 6 lots of 300 spaces (1,800 in total), 2 attendants per lot, and the nearest lot draws twice as many cars as the farthest.

| Strategy | Cars parked | Turned away | Average wait (min) | Longest wait (min) | Average walk (min) | Parking fees |
|---|---|---|---|---|---|---|
| `assigned_lot` | 1,571 ± 32 | 129 ± 32 | 0.2 ± 0.0 | 2.0 ± 0.5 | 3.8 ± 0.0 | $39,275 |
| `shortest_line` | 1,700 ± 0 | 0 ± 0 | 0.0 ± 0.0 | 0.3 ± 0.0 | 4.0 ± 0.0 | $42,500 |
| `most_free_spaces` | 1,700 ± 0 | 0 ± 0 | 0.0 ± 0.0 | 0.2 ± 0.0 | 4.0 ± 0.0 | $42,500 |

There was room for every car (1,700 cars, 1,800 spaces), but with `assigned_lot` the nearest lots filled up while the farthest ones stayed partly empty. About 129 cars were turned away, which is about $3,200 in lost fees. The two smarter strategies parked everyone with almost no waiting. The cost is a slightly longer average walk (4.0 minutes against 3.8), because some cars use the farther lots.

**Scale.** The same code with only the parameters changed. Cars arrive over 90 minutes (10 for the demo). These are single runs at a speed of 600, so the waits are left out for the reason given in the stadium scale table.

| Cars | Lots x attendants | Attendant threads | Producer threads | Turned away | Real time (s) |
|---|---|---|---|---|---|
| 150 | 3 x 1 | 3 | 1 | 38 | 1.1 |
| 1,700 | 6 x 2 | 12 | 2 | 179 | 9.1 |
| 50,000 | 30 x 6 | 180 | 4 | 0 | 9.1 |
| 200,000 | 100 x 6 | 600 | 8 | 0 | 11.3 |

The demo turns cars away because it only has 120 spaces for 150 cars, and the 1,700 car row uses `assigned_lot`, which turned away 129 ± 32 cars over 10 runs, so 179 is one ordinary run. The other two rows use `most_free_spaces`.

---

# Extra credit: The whole event (parking, then the gates)

The stadium and the parking lots also run together. Cars arrive and park, and the fans in each car walk from their lot to the stadium and join the line at a gate. The two halves now feed each other: a slow lot entrance starves the gates, and a slow gate backs the fans up. The code is in `src/event`.

## How to run

In IntelliJ, open `app.SimulatorApp` and click the third tab, "Whole event". The left of the window has the controls for both halves, the middle shows the parking lots and the stadium gates side by side, and the right has the numbers for the whole event. Start, Pause, Resume, Reset and the speed slider act on both halves at once.

To run it without a window and print the results:

    java -cp out event.EventConsole football lots=8 gates=12 fanspercar=4

`EventConsole` takes a preset (`demo`, `football`, `concert` or `stress`) and any of these settings: `cars`, `lots`, `spaces`, `attendants`, `pay`, `minutes`, `lotstrategy`, `gates`, `scanners`, `scan`, `gatestrategy`, `fanspercar`, `speed`, `streetlimit` and `gatecrowd`. The meaning of each is in the parameter tables above.

## What is new

| Setting | Default (football) | What it does |
|---|---|---|
| Street limit (`streetlimit`) and crowd limit (`gatecrowd`) | 0 (no limit) | The street limit at the lots and the crowd limit at the gates, as in the parking and stadium parameter tables. Fans who give up at a gate are counted in the results. |
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

Each row is the mean ± standard deviation over 10 runs of `experiments.Replications event`, with a different random seed in each run, at a speed of 120.

| Event | Gates x scanners | Parking average wait (min) | Gate average wait (min) | Gate longest wait (min) | Last fan inside (min) |
|---|---|---|---|---|---|
| Football (about 1,570 cars parked, 4,700 fans) | 8 x 2 | 0.2 ± 0.0 | 0.3 ± 0.1 | 2.5 ± 0.7 | 96.7 ± 0.8 |
| Football, same cars | 12 x 2 | 0.2 ± 0.0 | 0.0 ± 0.0 | 0.6 ± 0.1 | 96.7 ± 0.8 |
| Concert (about 910 cars parked, 2,730 fans) | 6 x 3 | 7.3 ± 0.5 | 0.1 ± 0.0 | 0.6 ± 0.2 | 71.7 ± 1.6 |

**The bottleneck moves.** At the football game the gates are where the longest waits are: with 8 gates of 2 scanners the longest gate wait was 2.5 minutes, and 12 gates cut it to 0.6. The last fan was inside at the same moment either way (96.7 minutes), because the last fans arrive when the last cars park, so more gates change the waits but not the finishing time. At the concert the parking lots are the problem: the cash payments at 8 attendants gave an average wait of 7.3 minutes at the lots against 0.1 at the gates, and the last fan was inside at minute 71.7 although the cars arrive over 45 minutes. The same concert fans, all arriving at the gates at once with no parking in front, gave an average wait of 2.6 ± 0.1 minutes at the gates in the stadium on its own (5 runs). The parking lots spread the crowd out, so the gates see a steadier stream, and it also means that adding more scanners to the concert would do nothing, while adding attendants would help.

The stress test is the largest run. At a speed of 600, 50,000 cars and 100,000 fans took about 15 seconds of real time with 180 attendant threads, 120 scanner threads and a few producer threads. Its waits are not reported, for the reason given in the stadium scale table.

---

# Checking that the results can be trusted

A simulation is only useful if its numbers are right. This part of the project shows how that was checked: automatic tests, a comparison with queueing theory, repeated runs, and a comparison of the thread design with the obvious alternative. The code for the checks is in `test/` and `src/experiments`.

## Tests

The `test/` folder has an automatic test suite. It needs nothing but the JDK, so there is no Maven, Gradle or JUnit to install. From the project folder:

    javac -d out src/*/*.java test/*/*.java
    java -cp out runner.RunAllTests

It prints PASS or FAIL for every test, takes between 15 and 45 seconds (the tests wait on real timers, and they are faster when the computer's timer is accurate), and exits with 1 if anything fails. In IntelliJ, mark the `test` folder as Test Sources Root and run `RunAllTests`. The tests are plain Java with a very small helper (`runner.Check`), and each one could become a JUnit `@Test` method by renaming the helpers.

| Test file | What it checks |
|---|---|
| `common/ArrivalLineTest` | The bounded buffer that everything is built on: items leave in the order they arrived even through the overflow list, arrivals are released by their time, stop values come last and never show in the GUI views, the crowd limit, and a stress test with 4 producers and 8 consumers handling 20,000 items where every item must be taken exactly once |
| `common/SimClockTest` | The clock freezes when paused, continues from the same moment, changes speed without a jump, and `sleepSim` waits longer when paused. The arrival patterns start at 0, end at 1 and never go backwards |
| `stadium/StadiumTest` | Settings are checked with clear messages. For every line strategy and producer count, every fan is admitted exactly once, the per-gate counts add up, and no fan is left in a line. A lane of size 1 under heavy load. The gate bias. Pause freezes the time and stop leaves no threads. The crowd limit: fans admitted plus fans who gave up equals every fan |
| `parking/ParkingTest` | Cars parked plus turned away plus driven on equals every car. No lot ever has more cars than spaces, even with a lot of one space and three busy attendants. A lot only turns cars away when it is full. Choosing the lot with free spaces avoids turning cars away |
| `event/EventTest` | The fans who get in equal the cars that parked times the fans per car, for every pair of strategies. Cars that are turned away bring no fans. Pause, resume and a speed change keep both halves in step, and the shared clock stops at the end |
| `experiments/ExperimentsTest` | The statistics and the queueing formulas against answers worked out by hand |

**Do the tests catch real bugs?** To find out, `synchronized` was removed from `ArrivalLine.join` and `ArrivalLine.promoteOutside` in a scratch copy of the project. The 20,000 item stress test then failed in 5 of 5 runs, for example with `items taken in total: expected 20000 but was 20019`, which means some items were handed out twice. With `synchronized` in place the same test passes every time, and the whole suite passed 6 runs in a row, with the computer's timer both accurate and coarse.

The file `.github/workflows/tests.yml` runs the same compile and test commands on GitHub each time code is pushed. It was written here but has not been run on GitHub yet, so check the Actions tab after the first push.

## Validation against queueing theory

Queueing theory has exact answers for some simple cases, so they can be used to check the simulation. `experiments.Validation` runs 100 gates at once, each with one line and one scanner. Fans arrive at random (Poisson) times, which is done through the mode where fans are handed to the stadium from outside, and are shared out between the gates at random. Each gate is then an independent line with Poisson arrivals, which is the case the textbook formulas describe. The program measures the average wait at every gate and compares it with the formula.

The formula needs the arrival rate and the service times that really happened, because threads sleep a little longer than they are asked to. So the program records every fan's arrival, start and finish time through a listener on the stadium, measures the real mean and variance of the service time, and puts those into the formula. That leaves the line itself to be checked: first come first served, no fan lost or counted twice, and the waiting measured correctly. For one scanner the formula (Pollaczek-Khinchine) is exact. For three scanners sharing a gate's line it is the Allen-Cunneen approximation. The 95% interval comes from the spread between the gates. Fans who arrive in the first 15% and the last 10% of the run are left out so the start up and the draining do not count.

    java -cp out experiments.Validation            about 8 minutes
    java -cp out experiments.Validation --quick    about 2 minutes, less precise

| Case | Fans | Measured average wait (s) | 95% interval | Theory (s) | Difference |
|---|---|---|---|---|---|
| 1 scanner per gate, load 0.5 | 11,403 | 172.5 | 159.8 to 185.2 | 166.9 | +3.4% |
| 1 scanner per gate, load 0.7 | 15,625 | 396.8 | 366.7 to 426.9 | 392.6 | +1.1% |
| 3 scanners per gate, load 0.7 | 19,072 | 102.1 | 93.1 to 111.2 | 100.2 | +2.0% |

The theory value is inside the interval in every case, and the largest difference is under 4%. This does not test everything: the surge arrival patterns, the line strategies and the parking spaces are not covered by a formula. Those are covered by the tests above, which check that nothing is lost, duplicated or oversold.

## Repeated runs, and a mistake they found

Every experiment table in this README reports the mean ± standard deviation over 10 runs, each with a different random seed, produced by `experiments.Replications`. One run of a simulation that uses real threads is not enough to compare two settings, because the difference between them may be smaller than the difference between two runs of the same setting.

    java -cp out experiments.Replications              stadium, parking, event and crowd, 10 runs each
    java -cp out experiments.Replications stadium 5    one experiment (stadium, parking, event or crowd), 5 runs

A single experiment takes about 20 minutes at 10 runs, and the four are independent, so they can be run in separate terminals at the same time.

**The first version of these experiments was wrong, and the repeated runs showed it.** They were run at a speed of 600. At that speed a scan lasts about 12 ms of real time, which is shorter than the 15.6 ms tick of the Windows system timer when it is in its default state, and any program (a browser, an editor) can switch that timer between 1 ms and 15.6 ms while the simulation runs. A `Thread.sleep(1)` then takes 2 ms at one moment and 15 ms the next, so the scanners were sometimes several times slower than they were meant to be. The same football game gave an average wait of 0.7 minutes in one single run and 4.2 ± 0.3 minutes in ten runs a few minutes later. The football parking average wait was 1.3 minutes at a speed of 600 and is 0.2 minutes at a speed of 120. The experiments now run at a speed of 120, where a scan lasts about 60 ms, and `experiments.TimerCheck` measures how long `Thread.sleep(1)` really takes and prints it at the start and the end of each run, so a reader can see which state the timer was in. For the parking, event and crowd limit experiments it read between 1.8 and 2.1 ms at the start and the end (accurate). `SimClock` also now waits with one long sleep instead of many short ones, because on a coarse timer every short sleep is rounded up to the next tick. The queueing theory validation is not affected, because its scans last half a second of real time and it measures the real ones.

**The stadium line strategies were run twice.** The first time the timer check read 1.9 ms at the start and 15.4 ms at the end, and the average wait at the assigned gates was 0.9 ± 0.1 minutes. The second time, with a check before every run, the slowest reading was 15.6 ms, so the timer was coarse for at least one run, and the average wait was 0.7 ± 0.3 minutes. That is the table above. The two experiments differ by more than the spread inside either of them suggests, so a standard deviation over repeated runs only measures the noise of one session, and a change of the computer's state between sessions adds to it. Read the waits as roughly one minute or less for the assigned gates. The ranking of the strategies is many times larger than any of these differences and was the same every time.

## Why not one thread per fan?

The Costco lab made every customer a thread. That is the obvious design, so `experiments.ThreadModels` builds it, and a second version with virtual threads (Java 21), and compares them with the design in this project on the same workload: 40 gates of 3 scanners, a scan of 60 ms, a walk of 300 ms, and fans arriving over 6 seconds of real time. Every design must scan every fan. Each run is a separate Java process with a 2 GB heap and a small thread stack (`-Xss256k`).

    java -cp out experiments.ThreadModels --all    about 3 minutes, and it starts more than 20,000 threads

| Fans | Design | Time (s) | Peak platform threads | Peak heap (MB) |
|---|---|---|---|---|
| 1,000 | one platform thread per fan | 6.4 | 1,128 | 5 |
| 1,000 | one virtual thread per fan | 6.4 | 154 | 5 |
| 1,000 | objects and pools (this project) | 6.4 | 130 | 4 |
| 5,000 | one platform thread per fan | 6.7 | 5,127 | 21 |
| 5,000 | one virtual thread per fan | 6.4 | 154 | 13 |
| 5,000 | objects and pools (this project) | 6.4 | 130 | 4 |
| 20,000 | one platform thread per fan | 12.3 | 15,795 | 88 |
| 20,000 | one virtual thread per fan | 11.8 | 154 | 43 |
| 20,000 | objects and pools (this project) | 12.3 | 130 | 4 |
| 50,000 | one platform thread per fan | 28.5 | 21,792 | 157 |
| 50,000 | one virtual thread per fan | 26.9 | 154 | 115 |
| 50,000 | objects and pools (this project) | 27.7 | 130 | 8 |

These are single runs. All three designs scanned every fan at every size, and the time is the same, because the scanners are the slow part. What differs is the cost of holding the crowd:

- **One platform thread per fan** needs a thread for every fan who is waiting at the same moment: 5,127 threads for 5,000 fans, and 21,792 at the peak for 50,000. Its heap grew to 157 MB, and each thread also reserves stack memory outside the heap that this table does not count. It did not fail at 50,000 on this computer, but it was not tried above that, because 200,000 fans would need around 90,000 threads and could make the computer unresponsive. The Costco lab design is fine for 20 customers and is the wrong design for a stadium.
- **One virtual thread per fan** is a real alternative. It keeps the simple blocking `put` of the lab and needs only 154 platform threads, whatever the crowd. But its memory still grows with the crowd (115 MB at 50,000 fans), because every waiting fan keeps a stack, and nothing limits it.
- **Objects and pools**, the design of this project, used 130 threads and only 8 MB at 50,000 fans, because a fan is a small object and only the fans who are waiting stay in memory. The cost is more code (the overflow list and the producer threads) and the changed meaning of the bounded buffer described in the scaling section. Virtual threads would be a reasonable choice for a project that does not need to draw or count every waiting fan.

The peak heap is sampled every 50 ms from the memory in use, so it includes garbage that has not been collected yet, and the small numbers are not exact.

## Known limitations

- **The simulation runs in real time with real threads.** It is not a discrete-event simulation, which would jump from event to event, run much faster and give the same answer every time. Real threads were used on purpose, because the lab is about threads, but it has costs. Results change a little from run to run, which is why the experiments report a mean and a standard deviation. The operating system's sleep is only accurate to a few milliseconds, or about 15 ms when the Windows timer is in its default state, and the timer can change while other programs run. At a speed of 600 a scan lasts only about 12 ms of real time and the waits came out several times too long, so use a speed of about 120 or lower for experiments. The experiments program checks the timer before every run. The validation above uses scans of half a second of real time and measures the real ones, so it is not affected.
- **Counts shown while the simulation runs can be off by a few fans**, because fans move between places while they are counted. The final numbers are exact, and the tests check them.
- **The outside list is unbounded unless the crowd limit is set**, as described in the scaling section. With the limit at 0, memory grows with the crowd.
- **Fans who give up, and cars that drive on, simply disappear at the back of the line in the pictures.** They are counted in the numbers but not animated. Cars that are turned away because a lot is full are animated.
- **The model is simple.** Cars never leave a lot, nobody leaves the stadium, a turned-away car does not look for another lot, every fan in a car walks and arrives together, and the scan and payment times are uniform between half and one and a half times the average.
- **The validation covers one kind of line** (Poisson arrivals, first come first served). The surge patterns and the strategies are checked for correctness by the tests but not against a formula.
- **The picture code is long.** `ParkingView` is about 1,000 lines and would be easier to read split into one class for each layout.

## Project layout

| Folder | What is in it |
|---|---|
| `src/costco` | The original Costco lab, unchanged |
| `src/common` | Code shared by the stadium, the parking and the whole event: `ArrivalLine` (the bounded buffer), `SimClock`, `ArrivalShape`, `HistoryChart` and `Form` (the control panel helpers) |
| `src/stadium` | The stadium entry simulation and its window |
| `src/parking` | The parking lot simulation and its window |
| `src/event` | The whole event, which links parking to the stadium, and its tab |
| `src/app` | `SimulatorApp`, the window with all three tabs |
| `src/experiments` | `Validation`, `Replications` and `ThreadModels`, plus their helpers |
| `test` | The automatic tests and the small test helper |
