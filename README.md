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
